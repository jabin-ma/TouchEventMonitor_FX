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
import com.android.annotations.concurrency.GuardedBy
import com.android.ddmlib.input.android.Command
import com.android.ddmlib.input.android.InputManager
import com.android.ddmlib.log.LogReceiver
import com.android.ddmlib.monkey.NetworkMonkey
import com.google.common.base.CharMatcher
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.util.concurrent.Atomics
import java.io.*
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.*
import java.util.regex.Pattern


/**
 * A Device. It can be a physical device or an emulator.
 */
internal class Device(val monitor: DeviceMonitor,
                      /**
                       * Serial number of the device
                       */
                      /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getSerialNumber()
     */
                      override val serialNumber: String, deviceState: IDevice.DeviceState) : IDevice {
    override val inputManager: InputManager by lazy {
        InputManager(this)
    }

    /**
     * Name of the AVD
     */
    /**
     * Sets the name of the AVD
     */
    override var avdName: String? = null
        set(avdName) {
            if (!isEmulator) {
                throw IllegalArgumentException(
                        "Cannot set the AVD name of the device is not an emulator")
            }

            field = avdName
        }

    /**
     * State of the device.
     */
    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getState()
     */
    /**
     * Changes the state of the device.
     */
    override var state: IDevice.DeviceState? = null

    /**
     * Device properties.
     */
    private val mPropFetcher = PropertyFetcher(this)
    private val mMountPoints = HashMap<String, String>()

    private val mBatteryFetcher = BatteryFetcher(this)

    @GuardedBy("mClients")
    private val mClients = ArrayList<Client>()

    /**
     * Maps pid's of clients in [.mClients] to their package name.
     */
    private val mClientInfo = ConcurrentHashMap<Int, String>()

    /**
     * Socket for the connection monitoring client connection/disconnection.
     */
    /**
     * Returns the channel on which responses to the track-jdwp command will be available if it
     * has been set, null otherwise. The channel is set via [.setClientMonitoringSocket],
     * which is usually invoked when the device goes online.
     */
    /**
     * Sets the socket channel on which a track-jdwp command for this device has been sent.
     */
    @get:Nullable
    var clientMonitoringSocket: SocketChannel? = null

    private val mLastBatteryLevel: Int? = null
    private val mLastBatteryCheckTime: Long = 0

    /**
     * Flag indicating whether the device has the screen recorder binary.
     */
    private var mHasScreenRecorder: Boolean? = null

    /**
     * Cached list of hardware characteristics
     */
    private var mHardwareCharacteristics: Set<String>? = null

    private var mApiLevel: Int = 0
    private var mName: String? = null

    /**
     * Output receiver for "pm install package.apk" command line.
     */
    private class InstallReceiver : MultiLineReceiver() {

        var errorMessage: String? = null
            private set

        override fun processNewLines(lines: Array<String>) {
            for (line in lines) {
                if (!line.isEmpty()) {
                    if (line.startsWith(SUCCESS_OUTPUT)) {
                        errorMessage = null
                    } else {
                        val m = FAILURE_PATTERN.matcher(line)
                        if (m.matches()) {
                            errorMessage = m.group(1)
                        } else {
                            errorMessage = "Unknown failure"
                        }
                    }
                }
            }
        }

        override fun isCancelled(): Boolean {
            return false
        }

        companion object {

            private val SUCCESS_OUTPUT = "Success" //$NON-NLS-1$
            private val FAILURE_PATTERN = Pattern.compile("Failure\\s+\\[(.*)\\]") //$NON-NLS-1$
        }
    }

    override fun getName(): String? {
        if (mName != null) {
            return mName
        }

        if (isOnline) {
            // cache name only if device is online
            mName = constructName()
            return mName
        } else {
            return constructName()
        }
    }

    private fun constructName(): String {
        if (isEmulator) {
            val avdName = avdName
            if (avdName != null) {
                return String.format("%s [%s]", avdName, serialNumber)
            } else {
                return serialNumber
            }
        } else {
            var manufacturer: String? = null
            var model: String? = null

            try {
                manufacturer = cleanupStringForDisplay(getProperty(IDevice.Companion.PROP_DEVICE_MANUFACTURER))
                model = cleanupStringForDisplay(getProperty(IDevice.Companion.PROP_DEVICE_MODEL))
            } catch (e: Exception) {
                // If there are exceptions thrown while attempting to get these properties,
                // we can just use the serial number, so ignore these exceptions.
            }

            val sb = StringBuilder(20)

            if (manufacturer != null) {
                sb.append(manufacturer)
                sb.append(SEPARATOR)
            }

            if (model != null) {
                sb.append(model)
                sb.append(SEPARATOR)
            }

            sb.append(serialNumber)
            return sb.toString()
        }
    }

    private fun cleanupStringForDisplay(s: String?): String? {
        if (s == null) {
            return null
        }

        val sb = StringBuilder(s.length)
        for (i in 0..s.length - 1) {
            val c = s[i]

            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c))
            } else {
                sb.append('_')
            }
        }

        return sb.toString()
    }


    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getProperties()
     */
    override val properties: Map<String, String>
        get() = Collections.unmodifiableMap(mPropFetcher.properties)

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getPropertyCount()
     */
    override val propertyCount: Int
        get() = mPropFetcher.properties.size

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getProperty(java.lang.String)
     */
    override fun getProperty(name: String): String? {
        val future = mPropFetcher.getProperty(name)
        try {
            return future.get(GET_PROP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            // ignore
        } catch (e: ExecutionException) {
            // ignore
        } catch (e: java.util.concurrent.TimeoutException) {
            // ignore
        }

        return null
    }

    override fun arePropertiesSet(): Boolean {
        return mPropFetcher.arePropertiesSet()
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    override fun getPropertyCacheOrSync(name: String): String? {
        val future = mPropFetcher.getProperty(name)
        try {
            return future.get()
        } catch (e: InterruptedException) {
            // ignore
        } catch (e: ExecutionException) {
            // ignore
        }

        return null
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    override fun getPropertySync(name: String): String? {
        val future = mPropFetcher.getProperty(name)
        try {
            return future.get()
        } catch (e: InterruptedException) {
            // ignore
        } catch (e: ExecutionException) {
            // ignore
        }

        return null
    }

    @NonNull
    override fun getSystemProperty(@NonNull name: String): Future<String> {
        return mPropFetcher.getProperty(name)
    }

    override fun supportsFeature(@NonNull feature: IDevice.Feature): Boolean {
        when (feature) {
            IDevice.Feature.SCREEN_RECORD -> {
                if (apiLevel < 19) {
                    return false
                }
                if (mHasScreenRecorder == null) {
                    mHasScreenRecorder = hasBinary(SCREEN_RECORDER_DEVICE_PATH)
                }
                return mHasScreenRecorder!!
            }
            IDevice.Feature.PROCSTATS -> return apiLevel >= 19
            else -> return false
        }
    }

    // The full list of features can be obtained from /etc/permissions/features*
    // However, the smaller set of features we are interested in can be obtained by
    // reading the build characteristics property.
    override fun supportsFeature(@NonNull feature: IDevice.HardwareFeature): Boolean {
        if (mHardwareCharacteristics == null) {
            try {
                val characteristics = getProperty(IDevice.Companion.PROP_BUILD_CHARACTERISTICS) ?: return false

                mHardwareCharacteristics = Sets.newHashSet(Splitter.on(',').split(characteristics))
            } catch (e: Exception) {
                mHardwareCharacteristics = emptySet<String>()
            }

        }

        return mHardwareCharacteristics!!.contains(feature.characteristic)
    }

    private val apiLevel: Int
        get() {
            if (mApiLevel > 0) {
                return mApiLevel
            }

            try {
                val buildApi = getProperty(IDevice.Companion.PROP_BUILD_API_LEVEL)
                mApiLevel = if (buildApi == null) -1 else Integer.parseInt(buildApi)
                return mApiLevel
            } catch (e: Exception) {
                return -1
            }

        }

    private fun hasBinary(path: String): Boolean {
        val latch = CountDownLatch(1)
        val receiver = CollectingOutputReceiver(latch)
        try {
            executeShellCommand(receiver, "ls " + path)
        } catch (e: Exception) {
            return false
        }

        try {
            latch.await(LS_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            return false
        }

        val value = receiver.output.trim { it <= ' ' }
        return !value.endsWith("No such file or directory")
    }

    @Nullable
    override fun getMountPoint(@NonNull name: String): String? {
        var mount: String? = mMountPoints[name]
        if (mount == null) {
            try {
                mount = queryMountPoint(name)
                mMountPoints.put(name, mount)
            } catch (ignored: TimeoutException) {
            } catch (ignored: AdbCommandRejectedException) {
            } catch (ignored: ShellCommandUnresponsiveException) {
            } catch (ignored: IOException) {
            }

        }
        return mount
    }

    @Nullable
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    private fun queryMountPoint(@NonNull name: String): String {

        val ref = Atomics.newReference<String>()
        executeShellCommand(object : MultiLineReceiver() { //$NON-NLS-1$
            override fun isCancelled(): Boolean {
                return false
            }

            override fun processNewLines(lines: Array<String>) {
                for (line in lines) {
                    if (!line.isEmpty()) {
                        // this should be the only one.
                        ref.set(line)
                    }
                }
            }
        }, "echo $$name")
        return ref.get()
    }

    override fun toString(): String {
        return serialNumber + "-" + state
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isOnline()
     */
    override val isOnline: Boolean
        get() = state === IDevice.DeviceState.ONLINE

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isEmulator()
     */
    override val isEmulator: Boolean
        get() = serialNumber.matches(RE_EMULATOR_SN.toRegex())

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isOffline()
     */
    override val isOffline: Boolean
        get() = state === IDevice.DeviceState.OFFLINE

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isBootLoader()
     */
    override val isBootLoader: Boolean
        get() = state === IDevice.DeviceState.BOOTLOADER

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getSyncService()
     */
    override val syncService: SyncService?
        @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
        get() {
            val syncService = SyncService(AndroidDebugBridge.getSocketAddress(), this)
            if (syncService.openSync()) {
                return syncService
            }

            return null
        }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getFileListingService()
     */
    override val fileListingService: FileListingService
        get() = FileListingService(this)

    override val screenshot: RawImage
        @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
        get() = getScreenshot(0, TimeUnit.MILLISECONDS)

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    override fun getScreenshot(timeout: Long, unit: TimeUnit): RawImage {
        return AdbHelper.getFrameBuffer(AndroidDebugBridge.getSocketAddress(), this, timeout, unit)
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class, ShellCommandUnresponsiveException::class)
    override fun startScreenRecorder(remoteFilePath: String, options: ScreenRecorderOptions,
                                     receiver: IShellOutputReceiver) {
        executeShellCommand(receiver, 0, null, getScreenRecorderCommand(remoteFilePath, options))
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    override fun executeShellCommand(receiver: IShellOutputReceiver, command: String, vararg args: String) {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), this,
                receiver, DdmPreferences.getTimeOut(), command, *args)
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    override fun executeShellCommand(receiver: IShellOutputReceiver, command: Command, vararg args: String) {
        executeShellCommand(receiver, command.cmd, *command.getArgs(*args))
    }


    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    override fun executeShellCommand(receiver: IShellOutputReceiver,
                                     maxTimeToOutputResponse: Int, command: String, vararg args: String) {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), this,
                receiver, maxTimeToOutputResponse, command, *args)
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    override fun executeShellCommand(receiver: IShellOutputReceiver, maxTimeToOutputResponse: Int, command: Command, vararg args: String) {
        executeShellCommand(receiver, maxTimeToOutputResponse, command.cmd, *command.getArgs(*args))
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    override fun executeShellCommand(receiver: IShellOutputReceiver,
                                     maxTimeToOutputResponse: Long, maxTimeUnits: TimeUnit?, command: String, vararg args: String) {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), this,
                receiver, maxTimeToOutputResponse, maxTimeUnits, command, *args)
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    override fun executeShellCommand(receiver: IShellOutputReceiver, maxTimeToOutputResponse: Long, maxTimeUnits: TimeUnit, command: Command, vararg args: String) {
        executeShellCommand(receiver, maxTimeToOutputResponse, maxTimeUnits, command.cmd, *command.getArgs(*args))
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    override fun runEventLogService(receiver: LogReceiver) {
        AdbHelper.runEventLogService(AndroidDebugBridge.getSocketAddress(), this, receiver)
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    override fun runLogService(logname: String, receiver: LogReceiver) {
        AdbHelper.runLogService(AndroidDebugBridge.getSocketAddress(), this, logname, receiver)
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    override fun createForward(localPort: Int, remotePort: Int) {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort), //$NON-NLS-1$
                String.format("tcp:%d", remotePort))   //$NON-NLS-1$
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    override fun createForward(localPort: Int, remoteSocketName: String,
                               namespace: IDevice.DeviceUnixSocketNamespace) {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort), //$NON-NLS-1$
                String.format("%s:%s", namespace.type, remoteSocketName))   //$NON-NLS-1$
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    override fun removeForward(localPort: Int, remotePort: Int) {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort), //$NON-NLS-1$
                String.format("tcp:%d", remotePort))   //$NON-NLS-1$
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    override fun removeForward(localPort: Int, remoteSocketName: String,
                               namespace: IDevice.DeviceUnixSocketNamespace) {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort), //$NON-NLS-1$
                String.format("%s:%s", namespace.type, remoteSocketName))   //$NON-NLS-1$
    }

    init {
        state = deviceState
    }

    override fun hasClients(): Boolean {
        synchronized(mClients) {
            return !mClients.isEmpty()
        }
    }

    override val clients: Array<Client>
        get() = synchronized(mClients) {
            return mClients.toTypedArray()
        }

    override fun getClient(applicationName: String): Client? {
        synchronized(mClients) {
            for (c in mClients) {
                if (applicationName == c.clientData.clientDescription) {
                    return c
                }
            }
        }
        return null
    }

    fun addClient(client: Client) {
        synchronized(mClients) {
            mClients.add(client)
        }

        addClientInfo(client)
    }

    val clientList: List<Client>
        get() = mClients

    fun clearClientList() {
        synchronized(mClients) {
            mClients.clear()
        }

        clearClientInfo()
    }

    /**
     * Removes a [Client] from the list.

     * @param client the client to remove.
     * *
     * @param notify Whether or not to notify the listeners of a change.
     */
    fun removeClient(client: Client, notify: Boolean) {
        monitor.addPortToAvailableList(client.debuggerListenPort)
        synchronized(mClients) {
            mClients.remove(client)
        }
        if (notify) {
            monitor.server.deviceChanged(this, IDevice.Companion.CHANGE_CLIENT_LIST)
        }

        removeClientInfo(client)
    }

    fun update(changeMask: Int) {
        monitor.server.deviceChanged(this, changeMask)
    }

    fun update(client: Client, changeMask: Int) {
        monitor.server.clientChanged(client, changeMask)
        updateClientInfo(client, changeMask)
    }

    fun setMountingPoint(name: String, value: String) {
        mMountPoints.put(name, value)
    }

    private fun addClientInfo(client: Client) {
        val cd = client.clientData
        setClientInfo(cd.pid, cd.clientDescription)
    }

    private fun updateClientInfo(client: Client, changeMask: Int) {
        if (changeMask and Client.CHANGE_NAME == Client.CHANGE_NAME) {
            addClientInfo(client)
        }
    }

    private fun removeClientInfo(client: Client) {
        val pid = client.clientData.pid
        mClientInfo.remove(pid)
    }

    private fun clearClientInfo() {
        mClientInfo.clear()
    }

    private fun setClientInfo(pid: Int, pkgName: String?) {
        var pkgName = pkgName
        if (pkgName == null) {
            pkgName = UNKNOWN_PACKAGE
        }

        mClientInfo.put(pid, pkgName)
    }

    override fun getClientName(pid: Int): String {
        val pkgName = mClientInfo[pid]
        return pkgName ?: UNKNOWN_PACKAGE
    }

    @Throws(IOException::class, AdbCommandRejectedException::class, TimeoutException::class, SyncException::class)
    override fun pushFile(local: String, remote: String) {
        var sync: SyncService? = null
        try {
            val targetFileName = getFileName(local)

            Log.d(targetFileName, String.format("Uploading %1\$s onto device '%2\$s'",
                    targetFileName, serialNumber))

            sync = syncService
            if (sync != null) {
                val message = String.format("Uploading file onto device '%1\$s'",
                        serialNumber)
                Log.d(LOG_TAG, message)
                sync.pushFile(local, remote, SyncService.getNullProgressMonitor())
            } else {
                throw IOException("Unable to open sync connection!")
            }
        } catch (e: TimeoutException) {
            Log.e(LOG_TAG, "Error during Sync: timeout.")
            throw e

        } catch (e: SyncException) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1\$s", e.message))
            throw e

        } catch (e: IOException) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1\$s", e.message))
            throw e

        } finally {
            if (sync != null) {
                sync.close()
            }
        }
    }

    @Throws(IOException::class, AdbCommandRejectedException::class, TimeoutException::class, SyncException::class)
    override fun pullFile(remote: String, local: String) {
        var sync: SyncService? = null
        try {
            val targetFileName = getFileName(remote)

            Log.d(targetFileName, String.format("Downloading %1\$s from device '%2\$s'",
                    targetFileName, serialNumber))

            sync = syncService
            if (sync != null) {
                val message = String.format("Downloading file from device '%1\$s'",
                        serialNumber)
                Log.d(LOG_TAG, message)
                sync.pullFile(remote, local, SyncService.getNullProgressMonitor())
            } else {
                throw IOException("Unable to open sync connection!")
            }
        } catch (e: TimeoutException) {
            Log.e(LOG_TAG, "Error during Sync: timeout.")
            throw e

        } catch (e: SyncException) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1\$s", e.message))
            throw e

        } catch (e: IOException) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1\$s", e.message))
            throw e

        } finally {
            if (sync != null) {
                sync.close()
            }
        }
    }

    @Throws(InstallException::class)
    override fun installPackage(packageFilePath: String, reinstall: Boolean,
                                vararg extraArgs: String): String? {
        try {
            val remoteFilePath = syncPackageToDevice(packageFilePath)
            val result = installRemotePackage(remoteFilePath, reinstall, *extraArgs)
            removeRemotePackage(remoteFilePath)
            return result
        } catch (e: IOException) {
            throw InstallException(e)
        } catch (e: AdbCommandRejectedException) {
            throw InstallException(e)
        } catch (e: TimeoutException) {
            throw InstallException(e)
        } catch (e: SyncException) {
            throw InstallException(e)
        }

    }

    @Throws(InstallException::class)
    override fun installPackages(apkFilePaths: List<String>, timeOutInMs: Long, reinstall: Boolean,
                                 vararg extraArgs: String) {

        assert(!apkFilePaths.isEmpty())
        if (apiLevel < 21) {
            Log.w("Internal error : installPackages invoked with device < 21 for %s",
                    Joiner.on(",").join(apkFilePaths))

            if (apkFilePaths.size == 1) {
                installPackage(apkFilePaths[0], reinstall, *extraArgs)
                return
            }
            Log.e("Internal error : installPackages invoked with device < 21 for multiple APK : %s",
                    Joiner.on(",").join(apkFilePaths))
            throw InstallException(
                    "Internal error : installPackages invoked with device < 21 for multiple APK : " + Joiner.on(",").join(apkFilePaths))
        }
        val mainPackageFilePath = apkFilePaths[0]
        Log.d(mainPackageFilePath,
                String.format("Uploading main %1\$s and %2\$s split APKs onto device '%3\$s'",
                        mainPackageFilePath, Joiner.on(',').join(apkFilePaths),
                        serialNumber))

        try {
            // create a installation session.

            val extraArgsList = if (extraArgs != null)
                ImmutableList.copyOf(extraArgs)
            else
                ImmutableList.of<String>()

            val sessionId = createMultiInstallSession(apkFilePaths, extraArgsList, reinstall)
            if (sessionId == null) {
                Log.d(mainPackageFilePath, "Failed to establish session, quit installation")
                throw InstallException("Failed to establish session")
            }
            Log.d(mainPackageFilePath, String.format("Established session id=%1\$s", sessionId))

            // now upload each APK in turn.
            var index = 0
            var allUploadSucceeded = true
            while (allUploadSucceeded && index < apkFilePaths.size) {
                allUploadSucceeded = uploadAPK(sessionId, apkFilePaths[index], index++)
            }

            // if all files were upload successfully, commit otherwise abandon the installation.
            val command = if (allUploadSucceeded)
                "pm install-commit " + sessionId
            else
                "pm install-abandon " + sessionId
            val receiver = InstallReceiver()
            executeShellCommand(receiver, timeOutInMs, TimeUnit.MILLISECONDS, command)
            val errorMessage = receiver.errorMessage
            if (errorMessage != null) {
                val message = String.format("Failed to finalize session : %1\$s", errorMessage)
                Log.e(mainPackageFilePath, message)
                throw InstallException(message)
            }
            // in case not all files were upload and we abandoned the install, make sure to
            // notifier callers.
            if (!allUploadSucceeded) {
                throw InstallException("Unable to upload some APKs")
            }
        } catch (e: TimeoutException) {
            Log.e(LOG_TAG, "Error during Sync: timeout.")
            throw InstallException(e)

        } catch (e: IOException) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1\$s", e.message))
            throw InstallException(e)

        } catch (e: AdbCommandRejectedException) {
            throw InstallException(e)
        } catch (e: ShellCommandUnresponsiveException) {
            Log.e(LOG_TAG, String.format("Error during shell execution: %1\$s", e.message))
            throw InstallException(e)
        }

    }

    /**
     * Implementation of [com.android.ddmlib.MultiLineReceiver] that can receive a
     * Success message from ADB followed by a session ID.
     */
    private class MultiInstallReceiver : MultiLineReceiver() {

        @Nullable
        @get:Nullable
        var sessionId: String? = null
            internal set

        override fun isCancelled(): Boolean {
            return false
        }

        override fun processNewLines(lines: Array<String>) {
            for (line in lines) {
                val matcher = successPattern.matcher(line)
                if (matcher.matches()) {
                    sessionId = matcher.group(1)
                }
            }

        }

        companion object {

            private val successPattern = Pattern.compile("Success: .*\\[(\\d*)\\]")
        }
    }

    @Nullable
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    private fun createMultiInstallSession(apkFileNames: List<String>,
                                          @NonNull extraArgs: Collection<String>, reinstall: Boolean): String? {

        val apkFiles = Lists.transform(apkFileNames) { input -> File(input!!) }

        var totalFileSize = 0L
        for (apkFile in apkFiles) {
            if (apkFile.exists() && apkFile.isFile) {
                totalFileSize += apkFile.length()
            } else {
                throw IllegalArgumentException(apkFile.absolutePath + " is not a file")
            }
        }
        val parameters = StringBuilder()
        if (reinstall) {
            parameters.append("-r ")
        }
        parameters.append(Joiner.on(' ').join(extraArgs))
        val receiver = MultiInstallReceiver()
        val cmd = String.format("pm install-create %1\$s -S %2\$d",
                parameters.toString(),
                totalFileSize)
        executeShellCommand(receiver, DdmPreferences.getTimeOut(), cmd)
        return receiver.sessionId
    }

    private fun uploadAPK(sessionId: String, apkFilePath: String, uniqueId: Int): Boolean {
        Log.d(sessionId, String.format("Uploading APK %1\$s ", apkFilePath))
        val fileToUpload = File(apkFilePath)
        if (!fileToUpload.exists()) {
            Log.e(sessionId, String.format("File not found: %1\$s", apkFilePath))
            return false
        }
        if (fileToUpload.isDirectory) {
            Log.e(sessionId, String.format("Directory upload not supported: %1\$s", apkFilePath))
            return false
        }
        var baseName = if (fileToUpload.name.lastIndexOf('.') != -1)
            fileToUpload.name.substring(0, fileToUpload.name.lastIndexOf('.'))
        else
            fileToUpload.name

        baseName = UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS.replaceFrom(baseName, '_')

        val command = String.format("pm install-write -S %d %s %d_%s -",
                fileToUpload.length(), sessionId, uniqueId, baseName)

        Log.d(sessionId, String.format("Executing : %1\$s", command))
        var inputStream: InputStream? = null
        try {
            inputStream = BufferedInputStream(FileInputStream(fileToUpload))
            val receiver = InstallReceiver()
            AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(),
                    AdbHelper.AdbService.EXEC, this,
                    receiver, DdmPreferences.getTimeOut().toLong(), TimeUnit.MILLISECONDS, inputStream, command)
            if (receiver.errorMessage != null) {
                Log.e(sessionId, String.format("Error while uploading %1\$s : %2\$s", fileToUpload.name,
                        receiver.errorMessage))
            } else {
                Log.d(sessionId, String.format("Successfully uploaded %1\$s", fileToUpload.name))
            }
            return receiver.errorMessage == null
        } catch (e: Exception) {
            Log.e(sessionId, e)
            return false
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    Log.e(sessionId, e)
                }

            }

        }
    }

    @Throws(IOException::class, AdbCommandRejectedException::class, TimeoutException::class, SyncException::class)
    override fun syncPackageToDevice(localFilePath: String): String {
        var sync: SyncService? = null
        try {
            val packageFileName = getFileName(localFilePath)
            val remoteFilePath = String.format("/data/local/tmp/%1\$s", packageFileName) //$NON-NLS-1$

            Log.d(packageFileName, String.format("Uploading %1\$s onto device '%2\$s'",
                    packageFileName, serialNumber))

            sync = syncService
            if (sync != null) {
                val message = String.format("Uploading file onto device '%1\$s'",
                        serialNumber)
                Log.d(LOG_TAG, message)
                sync.pushFile(localFilePath, remoteFilePath, SyncService.getNullProgressMonitor())
            } else {
                throw IOException("Unable to open sync connection!")
            }
            return remoteFilePath
        } catch (e: TimeoutException) {
            Log.e(LOG_TAG, "Error during Sync: timeout.")
            throw e

        } catch (e: SyncException) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1\$s", e.message))
            throw e

        } catch (e: IOException) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1\$s", e.message))
            throw e

        } finally {
            if (sync != null) {
                sync.close()
            }
        }
    }

    @Throws(InstallException::class)
    override fun installRemotePackage(remoteFilePath: String, reinstall: Boolean,
                                      vararg extraArgs: String): String? {
        try {
            val receiver = InstallReceiver()
            val optionString = StringBuilder()
            if (reinstall) {
                optionString.append("-r ")
            }
            if (extraArgs != null) {
                optionString.append(Joiner.on(' ').join(extraArgs))
            }
            val cmd = String.format("pm install %1\$s \"%2\$s\"", optionString.toString(),
                    remoteFilePath)
            executeShellCommand(receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES, cmd)
            return receiver.errorMessage
        } catch (e: TimeoutException) {
            throw InstallException(e)
        } catch (e: AdbCommandRejectedException) {
            throw InstallException(e)
        } catch (e: ShellCommandUnresponsiveException) {
            throw InstallException(e)
        } catch (e: IOException) {
            throw InstallException(e)
        }

    }

    @Throws(InstallException::class)
    override fun removeRemotePackage(remoteFilePath: String) {
        try {
            executeShellCommand(
                    NullOutputReceiver(), INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES, String.format("rm \"%1\$s\"", remoteFilePath))
        } catch (e: IOException) {
            throw InstallException(e)
        } catch (e: TimeoutException) {
            throw InstallException(e)
        } catch (e: AdbCommandRejectedException) {
            throw InstallException(e)
        } catch (e: ShellCommandUnresponsiveException) {
            throw InstallException(e)
        }

    }

    @Throws(InstallException::class)
    override fun uninstallPackage(packageName: String): String? {
        try {
            val receiver = InstallReceiver()
            executeShellCommand(receiver, INSTALL_TIMEOUT_MINUTES,
                    TimeUnit.MINUTES, "pm uninstall " + packageName)
            return receiver.errorMessage
        } catch (e: TimeoutException) {
            throw InstallException(e)
        } catch (e: AdbCommandRejectedException) {
            throw InstallException(e)
        } catch (e: ShellCommandUnresponsiveException) {
            throw InstallException(e)
        } catch (e: IOException) {
            throw InstallException(e)
        }

    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#reboot()
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    override fun reboot(into: String) {
        AdbHelper.reboot(into, AndroidDebugBridge.getSocketAddress(), this)
    }

    override // use default of 5 minutes
    val batteryLevel: Int?
        @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class, ShellCommandUnresponsiveException::class)
        get() = getBatteryLevel((5 * 60 * 1000).toLong())

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class, ShellCommandUnresponsiveException::class)
    override fun getBatteryLevel(freshnessMs: Long): Int? {
        val futureBattery = getBattery(freshnessMs, TimeUnit.MILLISECONDS)
        try {
            return futureBattery.get()
        } catch (e: InterruptedException) {
            return null
        } catch (e: ExecutionException) {
            return null
        }

    }

    override val battery: Future<Int>
        @NonNull
        get() = getBattery(5, TimeUnit.MINUTES)

    @NonNull
    override fun getBattery(freshnessTime: Long, @NonNull timeUnit: TimeUnit): Future<Int> {
        return mBatteryFetcher.getBattery(freshnessTime, timeUnit)
    }

    override /* Try abiList (implemented in L onwards) otherwise fall back to abi and abi2. */ val abis: List<String>
        @NonNull
        get() {
            val abiList = getProperty(IDevice.PROP_DEVICE_CPU_ABI_LIST)
            if (abiList != null) {
                return Lists.newArrayList(*abiList.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            } else {
                val abis = Lists.newArrayListWithExpectedSize<String>(2)
                var abi = getProperty(IDevice.PROP_DEVICE_CPU_ABI)
                if (abi != null) {
                    abis.add(abi)
                }

                abi = getProperty(IDevice.PROP_DEVICE_CPU_ABI2)
                if (abi != null) {
                    abis.add(abi)
                }

                return abis
            }
        }

    override val density: Int
        get() {
            val densityValue = getProperty(IDevice.PROP_DEVICE_DENSITY)
            if (densityValue != null) {
                try {
                    return Integer.parseInt(densityValue)
                } catch (e: NumberFormatException) {
                    return -1
                }

            }

            return -1
        }

    override val language: String?
        get() = properties[IDevice.PROP_DEVICE_LANGUAGE]

    override val region: String?
        get() = getProperty(IDevice.PROP_DEVICE_REGION)


    override val monkey: NetworkMonkey
        @Throws(IOException::class)
        get() = NetworkMonkey.create(this)

    companion object {
        /**
         * Emulator Serial Number regexp.
         */
        val RE_EMULATOR_SN = "emulator-(\\d+)" //$NON-NLS-1$

        private val LOG_TAG = "Device"
        private val SEPARATOR = '-'
        private val UNKNOWN_PACKAGE = ""   //$NON-NLS-1$

        private val GET_PROP_TIMEOUT_MS: Long = 100
        private val INSTALL_TIMEOUT_MINUTES: Long

        init {
            val installTimeout = System.getenv("ADB_INSTALL_TIMEOUT")
            var time: Long = 4
            if (installTimeout != null) {
                try {
                    time = java.lang.Long.parseLong(installTimeout)
                } catch (e: NumberFormatException) {
                    // use default value
                }

            }
            INSTALL_TIMEOUT_MINUTES = time
        }

        /**
         * Path to the screen recorder binary on the device.
         */
        private val SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord"
        private val LS_TIMEOUT_SEC: Long = 2

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        fun getScreenRecorderCommand(@NonNull remoteFilePath: String,
                                     @NonNull options: ScreenRecorderOptions): String {
            val sb = StringBuilder()

            sb.append("screenrecord")
            sb.append(' ')

            if (options.width > 0 && options.height > 0) {
                sb.append("--size ")
                sb.append(options.width)
                sb.append('x')
                sb.append(options.height)
                sb.append(' ')
            }

            if (options.bitrateMbps > 0) {
                sb.append("--bit-rate ")
                sb.append(options.bitrateMbps * 1000000)
                sb.append(' ')
            }

            if (options.timeLimit > 0) {
                sb.append("--time-limit ")
                var seconds = TimeUnit.SECONDS.convert(options.timeLimit, options.timeLimitUnits)
                if (seconds > 180) {
                    seconds = 180
                }
                sb.append(seconds)
                sb.append(' ')
            }

            sb.append(remoteFilePath)

            return sb.toString()
        }

        private val UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'))
                .or(CharMatcher.anyOf("_-")).negate()

        /**
         * Helper method to retrieve the file name given a local file path

         * @param filePath full directory path to file
         * *
         * @return [String] file name
         */
        private fun getFileName(filePath: String): String {
            return File(filePath).name
        }
    }
}
