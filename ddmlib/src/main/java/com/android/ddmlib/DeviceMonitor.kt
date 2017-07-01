/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ddmlib

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.annotations.VisibleForTesting
import com.android.ddmlib.ClientData.DebuggerStatus
import com.android.ddmlib.DebugPortManager.IDebugPortProvider
import com.android.ddmlib.DeviceMonitor.DeviceListMonitorTask.State
import com.android.ddmlib.IDevice.DeviceState
import com.android.ddmlib.utils.DebuggerPorts
import com.android.utils.Pair
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Queues
import com.google.common.util.concurrent.Uninterruptibles
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * The [DeviceMonitor] monitors devices attached to adb.
 *
 *
 * On one thread, it runs the
 * [com.android.ddmlib.DeviceMonitor.DeviceListMonitorTask]. This
 * establishes a socket connection to the adb host, and issues a
 * [.ADB_TRACK_DEVICES_COMMAND]. It then monitors that socket for all
 * changes about device connection and device state.
 *
 *
 * For each device that is detected to be online, it then opens a new socket
 * connection to adb, and issues a "track-jdwp" command to that device. On this
 * connection, it monitors active clients on the device. Note: a single thread
 * monitors jdwp connections from all devices. The different socket connections
 * to adb (one per device) are multiplexed over a single selector.
 */
internal class DeviceMonitor
/**
 * Creates a new [DeviceMonitor] object and links it to the running
 * [AndroidDebugBridge] object.

 * @param server the running [AndroidDebugBridge].
 */
(@param:NonNull val server: AndroidDebugBridge) {

    private val mLengthBuffer2 = ByteArray(4)

    @Volatile private var mQuit = false
    private var mDeviceListMonitorTask: DeviceListMonitorTask? = null

    private var mSelector: Selector? = null

    private val mDevices = Lists.newCopyOnWriteArrayList<Device>()
    private val mDebuggerPorts = DebuggerPorts(DdmPreferences.getDebugPortBase())
    private val mClientsToReopen = HashMap<Client, Int>()
    private val mChannelsToRegister = Queues.newLinkedBlockingQueue<Pair<SocketChannel, Device>>()

    /**
     * Starts the monitoring.
     */
    fun start() {
        mDeviceListMonitorTask = DeviceListMonitorTask(server, DeviceListUpdateListener())
        Thread(mDeviceListMonitorTask, "Device List Monitor").start() //$NON-NLS-1$
    }

    /**
     * Stops the monitoring.
     */
    fun stop() {
        mQuit = true
        if (mDeviceListMonitorTask != null) {
            mDeviceListMonitorTask!!.stop()
        }
        // wake up the secondary loop by closing the selector.
        if (mSelector != null) {
            mSelector!!.wakeup()
        }
    }

    /**
     * Returns whether the monitor is currently connected to the debug bridge
     * server.
     */
    val isMonitoring: Boolean
        get() = mDeviceListMonitorTask != null && mDeviceListMonitorTask!!.isMonitoring

    val connectionAttemptCount: Int
        get() = if (mDeviceListMonitorTask == null) 0 else mDeviceListMonitorTask!!.connectionAttemptCount

    val restartAttemptCount: Int
        get() = if (mDeviceListMonitorTask == null) 0 else mDeviceListMonitorTask!!.restartAttemptCount

    fun hasInitialDeviceList(): Boolean {
        return mDeviceListMonitorTask != null && mDeviceListMonitorTask!!.hasInitialDeviceList()
    }

    /**
     * Returns the devices.
     */
    // Since this is a copy of write array list, we don't want to do a
    // compound operation
    // (toArray with an appropriate size) without locking, so we just let
    // the container provide
    // an appropriately sized array
    val devices: Array<Device>
        @NonNull
        get() = mDevices.toTypedArray()

    fun addClientToDropAndReopen(client: Client, port: Int) {
        synchronized(mClientsToReopen) {
            Log.d("DeviceMonitor", "Adding $client to list of client to reopen ($port).")
            if (mClientsToReopen[client] == null) {
                mClientsToReopen.put(client, port)
            }
        }
        mSelector!!.wakeup()
    }

    /**
     * Updates the device list with the new items received from the monitoring
     * service.
     */
    private fun updateDevices(@NonNull newList: MutableList<Device>) {
        val result = DeviceListComparisonResult.compare(mDevices, newList)
        for (device in result.removed) {
            removeDevice(device as Device)
            server.deviceDisconnected(device)
        }

        val newlyOnline = Lists.newArrayListWithExpectedSize<Device>(mDevices.size)

        for ((key, value) in result.updated) {
            val device = key as Device
            device.state = value
            device.update(IDevice.CHANGE_STATE)

            if (device.isOnline) {
                newlyOnline.add(device)
            }
        }

        for (device in result.added) {
            mDevices.add(device as Device)
            server.deviceConnected(device)
            if (device.isOnline) {
                newlyOnline.add(device)
            }
        }

        if (AndroidDebugBridge.getClientSupport()) {
            for (device in newlyOnline) {
                if (!startMonitoringDevice(device)) {
                    Log.e("DeviceMonitor", "Failed to start monitoring " + device.serialNumber)
                }
            }
        }

        for (device in newlyOnline) {
            queryAvdName(device)
        }
    }

    private fun removeDevice(@NonNull device: Device) {
        device.clearClientList()
        mDevices.remove(device)

        val channel = device.clientMonitoringSocket
        if (channel != null) {
            try {
                channel.close()
            } catch (e: IOException) {
                // doesn't really matter if the close fails.
            }

        }
    }

    /**
     * Starts a monitoring service for a device.

     * @param device the device to monitor.
     * *
     * @return true if success.
     */
    private fun startMonitoringDevice(@NonNull device: Device): Boolean {
        val socketChannel = openAdbConnection()

        if (socketChannel != null) {
            try {
                val result = sendDeviceMonitoringRequest(socketChannel, device)
                if (result) {

                    if (mSelector == null) {
                        startDeviceMonitorThread()
                    }

                    device.clientMonitoringSocket = socketChannel

                    socketChannel.configureBlocking(false)

                    try {
                        mChannelsToRegister.put(Pair.of(socketChannel, device))
                    } catch (e: InterruptedException) {
                        // the queue is unbounded, and isn't going to block
                    }

                    mSelector!!.wakeup()

                    return true
                }
            } catch (e: TimeoutException) {
                try {
                    // attempt to close the socket if needed.
                    socketChannel.close()
                } catch (e1: IOException) {
                    // we can ignore that one. It may already have been closed.
                }

                Log.d("DeviceMonitor", "Connection Failure when starting to monitor device '$device' : timeout")
            } catch (e: AdbCommandRejectedException) {
                try {
                    // attempt to close the socket if needed.
                    socketChannel.close()
                } catch (e1: IOException) {
                    // we can ignore that one. It may already have been closed.
                }

                Log.d("DeviceMonitor", "Adb refused to start monitoring device '" + device + "' : " + e.message)
            } catch (e: IOException) {
                try {
                    // attempt to close the socket if needed.
                    socketChannel.close()
                } catch (e1: IOException) {
                    // we can ignore that one. It may already have been closed.
                }

                Log.d("DeviceMonitor",
                        "Connection Failure when starting to monitor device '" + device + "' : " + e.message)
            }

        }

        return false
    }

    @Throws(IOException::class)
    private fun startDeviceMonitorThread() {
        mSelector = Selector.open()
        object : Thread("Device Client Monitor") { //$NON-NLS-1$
            override fun run() {
                deviceClientMonitorLoop()
            }
        }.start()
    }

    private fun deviceClientMonitorLoop() {
        do {
            try {
                val count = mSelector!!.select()

                if (mQuit) {
                    return
                }

                synchronized(mClientsToReopen) {
                    if (!mClientsToReopen.isEmpty()) {
                        val clients = mClientsToReopen.keys
                        val monitorThread = MonitorThread.getInstance()

                        for (client in clients) {
                            val device = client.deviceImpl
                            val pid = client.clientData.pid

                            monitorThread.dropClient(client, false /* notify */)

                            // This is kinda bad, but if we don't wait a bit,
                            // the client
                            // will never answer the second handshake!
                            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS)

                            var port = mClientsToReopen[client]

                            if (port == IDebugPortProvider.NO_STATIC_PORT) {
                                port = nextDebuggerPort
                            }
                            Log.d("DeviceMonitor", "Reopening " + client)
                            openClient(device, pid, port!!, monitorThread)
                            device.update(IDevice.CHANGE_CLIENT_LIST)
                        }

                        mClientsToReopen.clear()
                    }
                }

                // register any new channels
                while (!mChannelsToRegister.isEmpty()) {
                    try {
                        val data = mChannelsToRegister.take()
                        data.first.register(mSelector, SelectionKey.OP_READ, data.second)
                    } catch (e: InterruptedException) {
                        // doesn't block: this thread is the only reader and it
                        // reads only when
                        // there is data
                    }

                }

                if (count == 0) {
                    continue
                }

                val keys = mSelector!!.selectedKeys()
                val iter = keys.iterator()

                while (iter.hasNext()) {
                    val key = iter.next()
                    iter.remove()

                    if (key.isValid && key.isReadable) {
                        val attachment = key.attachment()

                        if (attachment is Device) {
                            val device = attachment

                            val socket = device.clientMonitoringSocket

                            if (socket != null) {
                                try {
                                    val length = readLength(socket, mLengthBuffer2)

                                    processIncomingJdwpData(device, socket, length)
                                } catch (ioe: IOException) {
                                    Log.d("DeviceMonitor", "Error reading jdwp list: " + ioe.message)
                                    socket.close()

                                    // restart the monitoring of that device
                                    if (mDevices.contains(device)) {
                                        Log.d("DeviceMonitor", "Restarting monitoring service for " + device)
                                        startMonitoringDevice(device)
                                    }
                                }

                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("DeviceMonitor", "Connection error while monitoring clients.")
            }

        } while (!mQuit)
    }

    @Throws(IOException::class)
    private fun processIncomingJdwpData(@NonNull device: Device, @NonNull monitorSocket: SocketChannel, length: Int) {

        // This methods reads @length bytes from the @monitorSocket channel.
        // These bytes correspond to the pids of the current set of processes on
        // the device.
        // It takes this set of pids and compares them with the existing set of
        // clients
        // for the device. Clients that correspond to pids that are not alive
        // anymore are
        // dropped, and new clients are created for pids that don't have a
        // corresponding Client.

        if (length >= 0) {
            // array for the current pids.
            val newPids = HashSet<Int>()

            // get the string data if there are any
            if (length > 0) {
                val buffer = ByteArray(length)
                val result = read(monitorSocket, buffer)

                // split each line in its own list and create an array of
                // integer pid
                val pids = result?.split("\n".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray() ?: arrayOfNulls<String>(0) //$NON-NLS-1$

                for (pid in pids) {
                    try {
                        newPids.add(Integer.valueOf(pid))
                    } catch (nfe: NumberFormatException) {
                        // looks like this pid is not really a number. Lets
                        // ignore it.
                        continue
                    }

                }
            }

            val monitorThread = MonitorThread.getInstance()

            val clients = device.clientList
            val existingClients = HashMap<Int, Client>()

            synchronized(clients) {
                for (c in clients) {
                    existingClients.put(c.clientData.pid, c)
                }
            }

            val clientsToRemove = HashSet<Client>()
            for (pid in existingClients.keys) {
                if (!newPids.contains(pid)) {
                    clientsToRemove.add(existingClients[pid]!!)
                }
            }

            val pidsToAdd = HashSet(newPids)
            pidsToAdd.removeAll(existingClients.keys)

            monitorThread.dropClients(clientsToRemove, false)

            // at this point whatever pid is left in the list needs to be
            // converted into Clients.
            for (newPid in pidsToAdd) {
                openClient(device, newPid, nextDebuggerPort, monitorThread)
            }

            if (!pidsToAdd.isEmpty() || !clientsToRemove.isEmpty()) {
                server.deviceChanged(device, IDevice.CHANGE_CLIENT_LIST)
            }
        }
    }

    private val nextDebuggerPort: Int
        get() = mDebuggerPorts.next()

    fun addPortToAvailableList(port: Int) {
        mDebuggerPorts.free(port)
    }

    private inner class DeviceListUpdateListener : DeviceListMonitorTask.UpdateListener {
        override fun connectionError(@NonNull e: Exception) {
            for (device in mDevices) {
                removeDevice(device)
                server.deviceDisconnected(device)
            }
        }

        override fun deviceListUpdate(@NonNull devices: Map<String, DeviceState>) {
            val l = Lists.newArrayListWithExpectedSize<Device>(devices.size)
            for ((key, value) in devices) {
                l.add(Device(this@DeviceMonitor, key, value))
            }
            // now merge the new devices with the old ones.
            updateDevices(l)
        }

        override fun stateChange(@NonNull state: State) {
            server.stateChange()
        }
    }

    @VisibleForTesting
    internal class DeviceListComparisonResult private constructor(@param:NonNull
                                                                  val updated: Map<IDevice, DeviceState>, @param:NonNull
                                                                  val added: List<IDevice>,
                                                                  @param:NonNull
                                                                  val removed: List<IDevice>) {
        companion object {

            @NonNull
            fun compare(@NonNull previous: List<IDevice>,
                        @NonNull current: MutableList<out IDevice>): DeviceListComparisonResult {
                var current = current
                current = Lists.newArrayList(current)

                val updated = Maps.newHashMapWithExpectedSize<IDevice, DeviceState>(current.size)
                val added = Lists.newArrayListWithExpectedSize<IDevice>(1)
                val removed = Lists.newArrayListWithExpectedSize<IDevice>(1)

                for (device in previous) {
                    val currentDevice = find(current, device)
                    if (currentDevice != null) {
                        if (currentDevice.state !== device.state) {
                            updated.put(device, currentDevice.state)
                        }
                        current.remove(currentDevice)
                    } else {
                        removed.add(device)
                    }
                }

                added.addAll(current)

                return DeviceListComparisonResult(updated, added, removed)
            }

            @Nullable
            private fun find(@NonNull devices: List<IDevice>, @NonNull device: IDevice): IDevice? {
                for (d in devices) {
                    if (d.serialNumber == device.serialNumber) {
                        return d
                    }
                }

                return null
            }
        }
    }

    @VisibleForTesting
    internal class DeviceListMonitorTask(@param:NonNull private val mBridge: AndroidDebugBridge, @param:NonNull private val mListener: UpdateListener) : Runnable {
        private val mLengthBuffer = ByteArray(4)

        private var mAdbConnection: SocketChannel? = null
        var isMonitoring = false
            private set
        var connectionAttemptCount = 0
            private set
        var restartAttemptCount = 0
            private set
        private var mInitialDeviceListDone = false

        @Volatile private var mQuit: Boolean = false

        interface UpdateListener {
            fun stateChange(@NonNull state: State)

            fun connectionError(@NonNull e: Exception)

            fun deviceListUpdate(@NonNull devices: Map<String, DeviceState>)
        }

//        init {
//        }

        private var state = State.STARTING

        private val mStateLock = Any()

        internal enum class State {
            STARTING, STOPED, STARTED
        }

        fun setState(newstate: State) {
            if (state == newstate)
                return
            synchronized(mStateLock) {
                state = newstate
            }
            mListener.stateChange(state)
        }

        fun getState(): State {
            synchronized(mStateLock) {
                return state
            }
        }

        override fun run() {
            do {
                if (mAdbConnection == null) {
                    Log.d("DeviceMonitor", "Opening adb connection")
                    mAdbConnection = openAdbConnection()
                    if (mAdbConnection == null) {
                        connectionAttemptCount++
                        Log.e("DeviceMonitor", "Connection attempts: " + connectionAttemptCount)
                        if (connectionAttemptCount > 10) {
                            if (!mBridge.startAdb()) {
                                restartAttemptCount++
                                Log.e("DeviceMonitor", "adb restart attempts: " + restartAttemptCount)
                            } else {
                                Log.i("DeviceMonitor", "adb restarted")
                                restartAttemptCount = 0
                            }
                        }
                        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS)
                    } else {
                        Log.d("DeviceMonitor", "Connected to adb for device monitoring")
                        connectionAttemptCount = 0
                    }
                }
                try {
                    if (mAdbConnection != null && !isMonitoring) {
                        isMonitoring = sendDeviceListMonitoringRequest()
                        setState(State.STARTED)
                    }

                    if (isMonitoring) {
                        val length = readLength(mAdbConnection!!, mLengthBuffer)

                        if (length >= 0) {
                            // read the incoming message
                            processIncomingDeviceData(length)

                            // flag the fact that we have build the list at
                            // least once.
                            mInitialDeviceListDone = true
                        }
                    }
                } catch (ace: AsynchronousCloseException) {
                    // this happens because of a call to Quit. We do nothing,
                    // and the loop will break.
                } catch (ioe: TimeoutException) {
                    handleExceptionInMonitorLoop(ioe)
                } catch (ioe: IOException) {
                    handleExceptionInMonitorLoop(ioe)
                }

            } while (!mQuit)
            setState(State.STOPED)
        }

        @Throws(TimeoutException::class, IOException::class)
        private fun sendDeviceListMonitoringRequest(): Boolean {
            val request = AdbHelper.formAdbRequest(ADB_TRACK_DEVICES_COMMAND)

            try {
                AdbHelper.write(mAdbConnection, request)
                val resp = AdbHelper.readAdbResponse(mAdbConnection, false)
                if (!resp.okay) {
                    // request was refused by adb!
                    Log.e("DeviceMonitor", "adb refused request: " + resp.message)
                }

                return resp.okay
            } catch (e: IOException) {
                Log.e("DeviceMonitor", "Sending Tracking request failed!")
                mAdbConnection!!.close()
                throw e
            }

        }

        private fun handleExceptionInMonitorLoop(@NonNull e: Exception) {
            if (!mQuit) {
                if (e is TimeoutException) {
                    Log.e("DeviceMonitor", "Adb connection Error: timeout")
                } else {
                    Log.e("DeviceMonitor", "Adb connection Error:" + e.message)
                }
                isMonitoring = false
                if (mAdbConnection != null) {
                    try {
                        mAdbConnection!!.close()
                    } catch (ioe: IOException) {
                        // we can safely ignore that one.
                    }

                    mAdbConnection = null

                    mListener.connectionError(e)
                }
            }
        }

        /**
         * Processes an incoming device message from the socket
         */
        @Throws(IOException::class)
        private fun processIncomingDeviceData(length: Int) {
            val result: Map<String, DeviceState>
            if (length <= 0) {
                result = emptyMap<String, DeviceState>()
            } else {
                val response = read(mAdbConnection!!, ByteArray(length))
                result = parseDeviceListResponse(response)
            }

            mListener.deviceListUpdate(result)
        }

        fun hasInitialDeviceList(): Boolean {
            return mInitialDeviceListDone
        }

        fun stop() {
            mQuit = true

            // wakeup the main loop thread by closing the main connection to
            // adb.
            if (mAdbConnection != null) {
                try {
                    mAdbConnection!!.close()
                } catch (ignored: IOException) {
                }

            }
        }

        companion object {

            @VisibleForTesting
            fun parseDeviceListResponse(@Nullable result: String?): Map<String, DeviceState> {
                val deviceStateMap = Maps.newHashMap<String, DeviceState>()
                val devices = result?.split("\n".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray() ?: arrayOfNulls<String>(0) //$NON-NLS-1$

                devices.filterNotNull().forEach {
                    val param = it.split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray() //$NON-NLS-1$
                    if (param.size == 2) {
                        // new adb uses only serial numbers to identify devices
                        deviceStateMap.put(param[0], DeviceState.getState(param[1]))
                    }
                }
//                for (d in devices)
//                {
//                    val param = d.split("\t".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray() //$NON-NLS-1$
//                    if (param.size == 2) {
//                        // new adb uses only serial numbers to identify devices
//                        deviceStateMap.put(param[0], DeviceState.getState(param[1]))
//                    }
//                }
                return deviceStateMap
            }
        }
    }

    companion object {
        private val ADB_TRACK_DEVICES_COMMAND = "host:track-devices"
        private val ADB_TRACK_JDWP_COMMAND = "track-jdwp"

        /**
         * Attempts to connect to the debug bridge server.

         * @return a connect socket if success, null otherwise
         */
        @Nullable
        private fun openAdbConnection(): SocketChannel? {
            try {
                val adbChannel = SocketChannel.open(AndroidDebugBridge.getSocketAddress())
                adbChannel.socket().tcpNoDelay = true
                return adbChannel
            } catch (e: IOException) {
                return null
            }

        }

        private fun queryAvdName(@NonNull device: Device) {
            if (!device.isEmulator) {
                return
            }

            val console = EmulatorConsole.getConsole(device)
            if (console != null) {
                device.avdName = console.avdName
                console.close()
            }
        }

        @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
        private fun sendDeviceMonitoringRequest(@NonNull socket: SocketChannel, @NonNull device: Device): Boolean {

            try {
                AdbHelper.setDevice(socket, device)
                AdbHelper.write(socket, AdbHelper.formAdbRequest(ADB_TRACK_JDWP_COMMAND))
                val resp = AdbHelper.readAdbResponse(socket, false)

                if (!resp.okay) {
                    // request was refused by adb!
                    Log.e("DeviceMonitor", "adb refused request: " + resp.message)
                }

                return resp.okay
            } catch (e: TimeoutException) {
                Log.e("DeviceMonitor", "Sending jdwp tracking request timed out!")
                throw e
            } catch (e: IOException) {
                Log.e("DeviceMonitor", "Sending jdwp tracking request failed!")
                throw e
            }

        }

        /**
         * Opens and creates a new client.
         */
        private fun openClient(@NonNull device: Device, pid: Int, port: Int, @NonNull monitorThread: MonitorThread) {

            val clientSocket: SocketChannel
            try {
                clientSocket = AdbHelper.createPassThroughConnection(AndroidDebugBridge.getSocketAddress(), device, pid)

                // required for Selector
                clientSocket.configureBlocking(false)
            } catch (uhe: UnknownHostException) {
                Log.d("DeviceMonitor", "Unknown Jdwp pid: " + pid)
                return
            } catch (e: TimeoutException) {
                Log.w("DeviceMonitor", "Failed to connect to client '$pid': timeout")
                return
            } catch (e: AdbCommandRejectedException) {
                Log.w("DeviceMonitor", "Adb rejected connection to client '" + pid + "': " + e.message)
                return

            } catch (ioe: IOException) {
                Log.w("DeviceMonitor", "Failed to connect to client '" + pid + "': " + ioe.message)
                return
            }

            createClient(device, pid, clientSocket, port, monitorThread)
        }

        /**
         * Creates a client and register it to the monitor thread
         */
        private fun createClient(@NonNull device: Device, pid: Int, @NonNull socket: SocketChannel, debuggerPort: Int,
                                 @NonNull monitorThread: MonitorThread) {

            /*
         * Successfully connected to something. Create a Client object, add it
		 * to the list, and initiate the JDWP handshake.
		 */

            val client = Client(device, socket, pid)

            if (client.sendHandshake()) {
                try {
                    if (AndroidDebugBridge.getClientSupport()) {
                        client.listenForDebugger(debuggerPort)
                    }
                } catch (ioe: IOException) {
                    client.clientData.debuggerConnectionStatus = DebuggerStatus.ERROR
                    Log.e("ddms", "Can't bind to local $debuggerPort for debugger")
                    // oh well
                }

                client.requestAllocationStatus()
            } else {
                Log.e("ddms", "Handshake with $client failed!")
                /*
			 * The handshake send failed. We could remove it now, but if the
			 * failure is "permanent" we'll just keep banging on it and getting
			 * the same result. Keep it in the list with its "error" state so we
			 * don't try to reopen it.
			 */
            }

            if (client.isValid) {
                device.addClient(client)
                monitorThread.addClient(client)
            }
        }

        /**
         * Reads the length of the next message from a socket.

         * @param socket The [SocketChannel] to read from.
         * *
         * @return the length, or 0 (zero) if no data is available from the socket.
         * *
         * @throws IOException if the connection failed.
         */
        @Throws(IOException::class)
        private fun readLength(@NonNull socket: SocketChannel, @NonNull buffer: ByteArray): Int {
            val msg = read(socket, buffer)

            if (msg != null) {
                try {
                    return Integer.parseInt(msg, 16)
                } catch (nfe: NumberFormatException) {
                    // we'll throw an exception below.
                }

            }

            // we receive something we can't read. It's better to reset the
            // connection at this point.
            throw IOException("Unable to read length")
        }

        /**
         * Fills a buffer by reading data from a socket.

         * @return the content of the buffer as a string, or null if it failed to
         * * convert the buffer.
         * *
         * @throws IOException if there was not enough data to fill the buffer
         */
        @Nullable
        @Throws(IOException::class)
        private fun read(@NonNull socket: SocketChannel, @NonNull buffer: ByteArray): String? {
            val buf = ByteBuffer.wrap(buffer, 0, buffer.size)

            while (buf.position() != buf.limit()) {
                val count: Int

                count = socket.read(buf)
                if (count < 0) {
                    throw IOException("EOF")
                }
            }

            try {
                String
                return String(buffer, 0, buf.position(), Charset.forName(AdbHelper.DEFAULT_ENCODING))
            } catch (e: UnsupportedEncodingException) {
                return null
            }

        }
    }
}
