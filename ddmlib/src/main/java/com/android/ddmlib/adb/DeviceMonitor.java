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

package com.android.ddmlib.adb;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.adb.AdbHelper.AdbResponse;
import com.android.ddmlib.adb.DeviceMonitor.DeviceListMonitorTask.State;
import com.android.ddmlib.adb.IDevice.DeviceState;
import com.android.ddmlib.utils.Log;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Uninterruptibles;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The {@link DeviceMonitor} monitors devices attached to adb.
 * <p>
 * On one thread, it runs the
 * {@link DeviceMonitor.DeviceListMonitorTask}. This
 * establishes a socket connection to the adb host, and issues a
 * {@link #ADB_TRACK_DEVICES_COMMAND}. It then monitors that socket for all
 * changes about device connection and device state.
 * <p>
 * For each device that is detected to be online, it then opens a new socket
 * connection to adb, and issues a "track-jdwp" command to that device. On this
 * connection, it monitors active clients on the device. Note: a single thread
 * monitors jdwp connections from all devices. The different socket connections
 * to adb (one per device) are multiplexed over a single selector.
 */
final class DeviceMonitor {


    private static final String ADB_TRACK_DEVICES_COMMAND = "host:track-devices";
    private final AndroidDebugBridge mServer;
    private DeviceListMonitorTask mDeviceListMonitorTask;
    private final List<Device> mDevices = Lists.newCopyOnWriteArrayList();
    /**
     * Creates a new {@link DeviceMonitor} object and links it to the running
     * {@link AndroidDebugBridge} object.
     *
     * @param server the running {@link AndroidDebugBridge}.
     */
    DeviceMonitor(@NonNull AndroidDebugBridge server) {
        mServer = server;
    }

    /**
     * Starts the monitoring.
     */
    void start() {
        mDeviceListMonitorTask = new DeviceListMonitorTask(mServer, new DeviceListUpdateListener());
        new Thread(mDeviceListMonitorTask, "Device List Monitor").start(); //$NON-NLS-1$
    }

    /**
     * Stops the monitoring.
     */
    void stop() {
        if (mDeviceListMonitorTask != null) {
            mDeviceListMonitorTask.stop();
        }
    }

    /**
     * Returns whether the monitor is currently connected to the debug bridge
     * server.
     */
    boolean isMonitoring() {
        return mDeviceListMonitorTask != null && mDeviceListMonitorTask.isMonitoring();
    }

    int getConnectionAttemptCount() {
        return mDeviceListMonitorTask == null ? 0 : mDeviceListMonitorTask.getConnectionAttemptCount();
    }

    int getRestartAttemptCount() {
        return mDeviceListMonitorTask == null ? 0 : mDeviceListMonitorTask.getRestartAttemptCount();
    }

    boolean hasInitialDeviceList() {
        return mDeviceListMonitorTask != null && mDeviceListMonitorTask.hasInitialDeviceList();
    }

    /**
     * Returns the devices.
     */
    @NonNull
    Device[] getDevices() {
        // Since this is a copy of write array list, we don't want to do a
        // compound operation
        // (toArray with an appropriate size) without locking, so we just let
        // the container provide
        // an appropriately sized array
        // noinspection ToArrayCallWithZeroLengthArrayArgument
        return mDevices.toArray(new Device[0]);
    }

    @NonNull
    AndroidDebugBridge getServer() {
        return mServer;
    }

    /**
     * Attempts to connect to the debug bridge server.
     *
     * @return a connect socket if success, null otherwise
     */
    @Nullable
    private static SocketChannel openAdbConnection() {
        try {
            SocketChannel adbChannel = SocketChannel.open(AndroidDebugBridge.getSocketAddress());
            adbChannel.socket().setTcpNoDelay(true);
            return adbChannel;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Updates the device list with the new items received from the monitoring
     * service.
     */
    private void updateDevices(@NonNull List<Device> newList) {
        DeviceListComparisonResult result = DeviceListComparisonResult.compare(mDevices, newList);
        for (IDevice device : result.removed) {
            removeDevice((Device) device);
            mServer.deviceDisconnected(device);
        }

        List<Device> newlyOnline = Lists.newArrayListWithExpectedSize(mDevices.size());

        for (Map.Entry<IDevice, DeviceState> entry : result.updated.entrySet()) {
            Device device = (Device) entry.getKey();
            device.setState(entry.getValue());
            device.update(Device.CHANGE_STATE);

            if (device.isOnline()) {
                newlyOnline.add(device);
            }
        }

        for (IDevice device : result.added) {
            mDevices.add((Device) device);
            mServer.deviceConnected(device);
            if (device.isOnline()) {
                newlyOnline.add((Device) device);
            }
        }
        for (Device device : newlyOnline) {
            queryAvdName(device);
        }
    }

    private void removeDevice(@NonNull Device device) {
        mDevices.remove(device);
    }

    private static void queryAvdName(@NonNull Device device) {
        if (!device.isEmulator()) {
            return;
        }

        EmulatorConsole console = EmulatorConsole.getConsole(device);
        if (console != null) {
            device.setAvdName(console.getAvdName());
            console.close();
        }
    }





    /**
     * Reads the length of the next message from a socket.
     *
     * @param socket The {@link SocketChannel} to mapping from.
     * @return the length, or 0 (zero) if no data is available from the socket.
     * @throws IOException if the connection failed.
     */
    private static int readLength(@NonNull SocketChannel socket, @NonNull byte[] buffer) throws IOException {
        String msg = read(socket, buffer);

        if (msg != null) {
            try {
                return Integer.parseInt(msg, 16);
            } catch (NumberFormatException nfe) {
                // we'll throw an exception below.
            }
        }

        // we receive something we can't mapping. It's better to reset the
        // connection at this point.
        throw new IOException("Unable to mapping length");
    }

    /**
     * Fills a buffer by reading data from a socket.
     *
     * @return the content of the buffer as a string, or null if it failed to
     * convert the buffer.
     * @throws IOException if there was not enough data to fill the buffer
     */
    @Nullable
    private static String read(@NonNull SocketChannel socket, @NonNull byte[] buffer) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(buffer, 0, buffer.length);

        while (buf.position() != buf.limit()) {
            int count;

            count = socket.read(buf);
            if (count < 0) {
                throw new IOException("EOF");
            }
        }

        try {
            return new String(buffer, 0, buf.position(), AdbHelper.DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private class DeviceListUpdateListener implements DeviceListMonitorTask.UpdateListener {
        @Override
        public void connectionError(@NonNull Exception e) {
            for (Device device : mDevices) {
                removeDevice(device);
                mServer.deviceDisconnected(device);
            }
        }

        @Override
        public void deviceListUpdate(@NonNull Map<String, DeviceState> devices) {
            List<Device> l = Lists.newArrayListWithExpectedSize(devices.size());
            for (Map.Entry<String, DeviceState> entry : devices.entrySet()) {
                l.add(new Device(DeviceMonitor.this, entry.getKey(), entry.getValue()));
            }
            // now merge the new devices with the old ones.
            updateDevices(l);
        }

        @Override
        public void stateChange(@NonNull State state) {
            mServer.stateChange();
        }
    }

    @VisibleForTesting
    static class DeviceListComparisonResult {
        @NonNull
        public final Map<IDevice, DeviceState> updated;
        @NonNull
        public final List<IDevice> added;
        @NonNull
        public final List<IDevice> removed;

        private DeviceListComparisonResult(@NonNull Map<IDevice, DeviceState> updated, @NonNull List<IDevice> added,
                                           @NonNull List<IDevice> removed) {
            this.updated = updated;
            this.added = added;
            this.removed = removed;
        }

        @NonNull
        public static DeviceListComparisonResult compare(@NonNull List<? extends IDevice> previous,
                                                         @NonNull List<? extends IDevice> current) {
            current = Lists.newArrayList(current);

            final Map<IDevice, DeviceState> updated = Maps.newHashMapWithExpectedSize(current.size());
            final List<IDevice> added = Lists.newArrayListWithExpectedSize(1);
            final List<IDevice> removed = Lists.newArrayListWithExpectedSize(1);

            for (IDevice device : previous) {
                IDevice currentDevice = find(current, device);
                if (currentDevice != null) {
                    if (currentDevice.getState() != device.getState()) {
                        updated.put(device, currentDevice.getState());
                    }
                    current.remove(currentDevice);
                } else {
                    removed.add(device);
                }
            }

            added.addAll(current);

            return new DeviceListComparisonResult(updated, added, removed);
        }

        @Nullable
        private static IDevice find(@NonNull List<? extends IDevice> devices, @NonNull IDevice device) {
            for (IDevice d : devices) {
                if (d.getSerialNumber().equals(device.getSerialNumber())) {
                    return d;
                }
            }

            return null;
        }
    }

    @VisibleForTesting
    static class DeviceListMonitorTask implements Runnable {
        private final byte[] mLengthBuffer = new byte[4];

        private final AndroidDebugBridge mBridge;
        private final UpdateListener mListener;

        private SocketChannel mAdbConnection = null;
        private boolean mMonitoring = false;
        private int mConnectionAttempt = 0;
        private int mRestartAttemptCount = 0;
        private boolean mInitialDeviceListDone = false;

        private volatile boolean mQuit;

        private interface UpdateListener {
            void stateChange(@NonNull State state);

            void connectionError(@NonNull Exception e);

            void deviceListUpdate(@NonNull Map<String, DeviceState> devices);
        }

        public DeviceListMonitorTask(@NonNull AndroidDebugBridge bridge, @NonNull UpdateListener listener) {
            mBridge = bridge;
            mListener = listener;
            setState(State.STARTING);
        }

        private State state = State.STOPED;

        private Object mStateLock = new Object();

        enum State {
            STARTING, STOPED, STARTED
        }

        public void setState(State newstate) {
            if (state == newstate)
                return;
            synchronized (mStateLock) {
                state = newstate;
            }
            mListener.stateChange(state);
        }

        public State getState() {
            synchronized (mStateLock) {
                return state;
            }
        }

        @Override
        public void run() {
            do {
                if (mAdbConnection == null) {
                    Log.d("DeviceMonitor", "Opening adb connection");
                    mAdbConnection = openAdbConnection();
                    if (mAdbConnection == null) {
                        mConnectionAttempt++;
                        Log.e("DeviceMonitor", "Connection attempts: " + mConnectionAttempt);
                        if (mConnectionAttempt > 10) {
                            if (!mBridge.startAdb()) {
                                mRestartAttemptCount++;
                                Log.e("DeviceMonitor", "adb restart attempts: " + mRestartAttemptCount);
                            } else {
                                Log.i("DeviceMonitor", "adb restarted");
                                mRestartAttemptCount = 0;
                            }
                        }
                        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
                    } else {
                        Log.d("DeviceMonitor", "Connected to adb for device monitoring");
                        mConnectionAttempt = 0;
                    }
                }
                try {
                    if (mAdbConnection != null && !mMonitoring) {
                        mMonitoring = sendDeviceListMonitoringRequest();
                        setState(State.STARTED);
                    }

                    if (mMonitoring) {
                        int length = readLength(mAdbConnection, mLengthBuffer);

                        if (length >= 0) {
                            // mapping the incoming message
                            processIncomingDeviceData(length);

                            // flag the fact that we have build the list at
                            // least once.
                            mInitialDeviceListDone = true;
                        }
                    }
                } catch (AsynchronousCloseException ace) {
                    // this happens because of a call to Quit. We do nothing,
                    // and the loop will break.
                } catch (TimeoutException ioe) {
                    handleExceptionInMonitorLoop(ioe);
                } catch (IOException ioe) {
                    handleExceptionInMonitorLoop(ioe);
                }
            } while (!mQuit);
            setState(State.STOPED);
        }

        private boolean sendDeviceListMonitoringRequest() throws TimeoutException, IOException {
            byte[] request = AdbHelper.formAdbRequest(ADB_TRACK_DEVICES_COMMAND);

            try {
                AdbHelper.write(mAdbConnection, request);
                AdbResponse resp = AdbHelper.readAdbResponse(mAdbConnection, false);
                if (!resp.okay) {
                    // request was refused by adb!
                    Log.e("DeviceMonitor", "adb refused request: " + resp.message);
                }

                return resp.okay;
            } catch (IOException e) {
                Log.e("DeviceMonitor", "Sending Tracking request failed!");
                mAdbConnection.close();
                throw e;
            }
        }

        private void handleExceptionInMonitorLoop(@NonNull Exception e) {
            if (!mQuit) {
                if (e instanceof TimeoutException) {
                    Log.e("DeviceMonitor", "Adb connection Error: timeout");
                } else {
                    Log.e("DeviceMonitor", "Adb connection Error:" + e.getMessage());
                }
                mMonitoring = false;
                if (mAdbConnection != null) {
                    try {
                        mAdbConnection.close();
                    } catch (IOException ioe) {
                        // we can safely ignore that one.
                    }
                    mAdbConnection = null;

                    mListener.connectionError(e);
                }
            }
        }

        /**
         * Processes an incoming device message from the socket
         */
        private void processIncomingDeviceData(int length) throws IOException {
            Map<String, DeviceState> result;
            if (length <= 0) {
                result = Collections.emptyMap();
            } else {
                String response = read(mAdbConnection, new byte[length]);
                result = parseDeviceListResponse(response);
            }

            mListener.deviceListUpdate(result);
        }

        @VisibleForTesting
        static Map<String, DeviceState> parseDeviceListResponse(@Nullable String result) {
            Map<String, DeviceState> deviceStateMap = Maps.newHashMap();
            String[] devices = result == null ? new String[0] : result.split("\n"); //$NON-NLS-1$

            for (String d : devices) {
                String[] param = d.split("\t"); //$NON-NLS-1$
                if (param.length == 2) {
                    // new adb uses only serial numbers to identify devices
                    deviceStateMap.put(param[0], DeviceState.getState(param[1]));
                }
            }
            return deviceStateMap;
        }

        boolean isMonitoring() {
            return mMonitoring;
        }

        boolean hasInitialDeviceList() {
            return mInitialDeviceListDone;
        }

        int getConnectionAttemptCount() {
            return mConnectionAttempt;
        }

        int getRestartAttemptCount() {
            return mRestartAttemptCount;
        }

        public void stop() {
            mQuit = true;

            // wakeup the main loop thread by closing the main connection to
            // adb.
            if (mAdbConnection != null) {
                try {
                    mAdbConnection.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
