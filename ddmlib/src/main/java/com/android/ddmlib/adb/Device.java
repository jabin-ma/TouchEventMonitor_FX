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
import com.android.ddmlib.controller.IRemoteController;
import com.android.ddmlib.controller.Type;
import com.android.ddmlib.input.InputManager;
import com.android.ddmlib.monkey.MonkeyTransport;
import com.android.ddmlib.utils.Log;
import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Atomics;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A Device. It can be a physical device or an emulator.
 */
final class Device implements IDevice {

    private static final String TAG="Device";
    /**
     * Emulator Serial Number regexp.
     */
    static final String RE_EMULATOR_SN = "emulator-(\\d+)"; //$NON-NLS-1$

    /**
     * Serial number of the device
     */
    private final String mSerialNumber;

    /**
     * Name of the AVD
     */
    private String mAvdName = null;

    /**
     * State of the device.
     */
    private DeviceState mState = null;

    /**
     * Device properties.
     */
    private final PropertyFetcher mPropFetcher = new PropertyFetcher(this);
    private final Map<String, String> mMountPoints = new HashMap<String, String>();

    private final BatteryFetcher mBatteryFetcher = new BatteryFetcher(this);

    private DeviceMonitor mMonitor;

    private static final String LOG_TAG = "Device";
    private static final char SEPARATOR = '-';
    private static final String UNKNOWN_PACKAGE = "";   //$NON-NLS-1$

    private static final long GET_PROP_TIMEOUT_MS = 100;
    private static final long INSTALL_TIMEOUT_MINUTES;

    static {
        String installTimeout = System.getenv("ADB_INSTALL_TIMEOUT");
        long time = 4;
        if (installTimeout != null) {
            try {
                time = Long.parseLong(installTimeout);
            } catch (NumberFormatException e) {
                // use default value
            }
        }
        INSTALL_TIMEOUT_MINUTES = time;
    }

    private Integer mLastBatteryLevel = null;
    private long mLastBatteryCheckTime = 0;

    /**
     * Path to the screen recorder binary on the device.
     */
    private static final String SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord";
    private static final long LS_TIMEOUT_SEC = 2;

    /**
     * Flag indicating whether the device has the screen recorder binary.
     */
    private Boolean mHasScreenRecorder;

    /**
     * Cached list of hardware characteristics
     */
    private Set<String> mHardwareCharacteristics;

    private int mApiLevel;
    private String mName;

    /**
     * Output receiver for "pm install package.apk" command line.
     */
    private static final class InstallReceiver extends MultiLineReceiver {

        private static final String SUCCESS_OUTPUT = "Success"; //$NON-NLS-1$
        private static final Pattern FAILURE_PATTERN = Pattern.compile("Failure\\s+\\[(.*)\\]"); //$NON-NLS-1$

        private String mErrorMessage = null;

        public InstallReceiver() {
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (!line.isEmpty()) {
                    if (line.startsWith(SUCCESS_OUTPUT)) {
                        mErrorMessage = null;
                    } else {
                        Matcher m = FAILURE_PATTERN.matcher(line);
                        if (m.matches()) {
                            mErrorMessage = m.group(1);
                        } else {
                            mErrorMessage = "Unknown failure";
                        }
                    }
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        public String getErrorMessage() {
            return mErrorMessage;
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#getSerialNumber()
     */
    @NonNull
    @Override
    public String getSerialNumber() {
        return mSerialNumber;
    }

    @Override
    public String getAvdName() {
        return mAvdName;
    }

    /**
     * Sets the name of the AVD
     */
    void setAvdName(String avdName) {
        if (!isEmulator()) {
            throw new IllegalArgumentException(
                    "Cannot set the AVD name of the device is not an emulator");
        }

        mAvdName = avdName;
    }

    @Override
    public String getName() {
        if (mName != null) {
            return mName;
        }

        if (isOnline()) {
            // cache name only if device is online
            mName = constructName();
            return mName;
        } else {
            return constructName();
        }
    }

    private String constructName() {
        if (isEmulator()) {
            String avdName = getAvdName();
            if (avdName != null) {
                return String.format("%s [%s]", avdName, getSerialNumber());
            } else {
                return getSerialNumber();
            }
        } else {
            String manufacturer = null;
            String model = null;

            try {
                manufacturer = cleanupStringForDisplay(getProperty(PROP_DEVICE_MANUFACTURER));
                model = cleanupStringForDisplay(getProperty(PROP_DEVICE_MODEL));
            } catch (Exception e) {
                // If there are exceptions thrown while attempting to get these properties,
                // we can just use the serial number, so ignore these exceptions.
            }

            StringBuilder sb = new StringBuilder(20);

            if (manufacturer != null) {
                sb.append(manufacturer);
                sb.append(SEPARATOR);
            }

            if (model != null) {
                sb.append(model);
                sb.append(SEPARATOR);
            }

            sb.append(getSerialNumber());
            return sb.toString();
        }
    }

    private String cleanupStringForDisplay(String s) {
        if (s == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('_');
            }
        }

        return sb.toString();
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#getState()
     */
    @Override
    public DeviceState getState() {
        return mState;
    }

    /**
     * Changes the state of the device.
     */
    void setState(DeviceState state) {
        mState = state;
    }


    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#getProperties()
     */
    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(mPropFetcher.getProperties());
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#getPropertyCount()
     */
    @Override
    public int getPropertyCount() {
        return mPropFetcher.getProperties().size();
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#getProperty(java.lang.String)
     */
    @Override
    public String getProperty(String name) {
        Future<String> future = mPropFetcher.getProperty(name);
        try {
            return future.get(GET_PROP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // ignore
        } catch (ExecutionException e) {
            // ignore
        } catch (java.util.concurrent.TimeoutException e) {
            // ignore
        }
        return null;
    }

    @Override
    public boolean arePropertiesSet() {
        return mPropFetcher.arePropertiesSet();
    }

    @Override
    public String getPropertyCacheOrSync(String name) throws TimeoutException,
            AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        Future<String> future = mPropFetcher.getProperty(name);
        try {
            return future.get();
        } catch (InterruptedException e) {
            // ignore
        } catch (ExecutionException e) {
            // ignore
        }
        return null;
    }

    @Override
    public String getPropertySync(String name) throws TimeoutException,
            AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        Future<String> future = mPropFetcher.getProperty(name);
        try {
            return future.get();
        } catch (InterruptedException e) {
            // ignore
        } catch (ExecutionException e) {
            // ignore
        }
        return null;
    }

    @NonNull
    @Override
    public Future<String> getSystemProperty(@NonNull String name) {
        return mPropFetcher.getProperty(name);
    }

    @Override
    public boolean supportsFeature(@NonNull Feature feature) {
        switch (feature) {
            case SCREEN_RECORD:
                if (getApiLevel() < 19) {
                    return false;
                }
                if (mHasScreenRecorder == null) {
                    mHasScreenRecorder = hasBinary(SCREEN_RECORDER_DEVICE_PATH);
                }
                return mHasScreenRecorder;
            case PROCSTATS:
                return getApiLevel() >= 19;
            default:
                return false;
        }
    }

    // The full list of features can be obtained from /etc/permissions/features*
    // However, the smaller set of features we are interested in can be obtained by
    // reading the build characteristics property.
    @Override
    public boolean supportsFeature(@NonNull HardwareFeature feature) {
        if (mHardwareCharacteristics == null) {
            try {
                String characteristics = getProperty(PROP_BUILD_CHARACTERISTICS);
                if (characteristics == null) {
                    return false;
                }

                mHardwareCharacteristics = Sets.newHashSet(Splitter.on(',').split(characteristics));
            } catch (Exception e) {
                mHardwareCharacteristics = Collections.emptySet();
            }
        }

        return mHardwareCharacteristics.contains(feature.getCharacteristic());
    }

    private int getApiLevel() {
        if (mApiLevel > 0) {
            return mApiLevel;
        }

        try {
            String buildApi = getProperty(PROP_BUILD_API_LEVEL);
            mApiLevel = buildApi == null ? -1 : Integer.parseInt(buildApi);
            return mApiLevel;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean hasBinary(String path) {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        try {
            executeShellCommand(receiver, "ls " + path);
        } catch (Exception e) {
            return false;
        }

        try {
            latch.await(LS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }

        String value = receiver.getOutput().trim();
        return !value.endsWith("No such file or directory");
    }

    @Nullable
    @Override
    public String getMountPoint(@NonNull String name) {
        String mount = mMountPoints.get(name);
        if (mount == null) {
            try {
                mount = queryMountPoint(name);
                mMountPoints.put(name, mount);
            } catch (TimeoutException ignored) {
            } catch (AdbCommandRejectedException ignored) {
            } catch (ShellCommandUnresponsiveException ignored) {
            } catch (IOException ignored) {
            }
        }
        return mount;
    }

    @Nullable
    private String queryMountPoint(@NonNull final String name)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {

        final AtomicReference<String> ref = Atomics.newReference();
        executeShellCommand(new MultiLineReceiver() { //$NON-NLS-1$
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void processNewLines(String[] lines) {
                for (String line : lines) {
                    if (!line.isEmpty()) {
                        // this should be the only one.
                        ref.set(line);
                    }
                }
            }
        }, "echo $" + name);
        return ref.get();
    }

    @Override
    public String toString() {
        return mSerialNumber + "-" + mState;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#isOnline()
     */
    @Override
    public boolean isOnline() {
        return mState == DeviceState.ONLINE;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#isEmulator()
     */
    @Override
    public boolean isEmulator() {
        return mSerialNumber.matches(RE_EMULATOR_SN);
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#isOffline()
     */
    @Override
    public boolean isOffline() {
        return mState == DeviceState.OFFLINE;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#isBootLoader()
     */
    @Override
    public boolean isBootLoader() {
        return mState == DeviceState.BOOTLOADER;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#getSyncService()
     */
    @Override
    public SyncService getSyncService()
            throws TimeoutException, AdbCommandRejectedException, IOException {
        SyncService syncService = new SyncService(AndroidDebugBridge.getSocketAddress(), this);
        if (syncService.openSync()) {
            return syncService;
        }

        return null;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#getFileListingService()
     */
    @Override
    public FileListingService getFileListingService() {
        return new FileListingService(this);
    }

    @Override
    public RawImage getScreenshot()
            throws TimeoutException, AdbCommandRejectedException, IOException {
        return getScreenshot(0, TimeUnit.MILLISECONDS);
    }

    @Override
    public RawImage getScreenshot(long timeout, TimeUnit unit)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        return AdbHelper.getFrameBuffer(AndroidDebugBridge.getSocketAddress(), this, timeout, unit);
    }

    @Override
    public void startScreenRecorder(String remoteFilePath, ScreenRecorderOptions options,
                                    IShellOutputReceiver receiver) throws TimeoutException, AdbCommandRejectedException,
            IOException, ShellCommandUnresponsiveException {
        executeShellCommand(receiver, 0, null, getScreenRecorderCommand(remoteFilePath, options), new String[]{});
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static String getScreenRecorderCommand(@NonNull String remoteFilePath,
                                           @NonNull ScreenRecorderOptions options) {
        StringBuilder sb = new StringBuilder();

        sb.append("screenrecord");
        sb.append(' ');

        if (options.width > 0 && options.height > 0) {
            sb.append("--size ");
            sb.append(options.width);
            sb.append('x');
            sb.append(options.height);
            sb.append(' ');
        }

        if (options.bitrateMbps > 0) {
            sb.append("--bit-rate ");
            sb.append(options.bitrateMbps * 1000000);
            sb.append(' ');
        }

        if (options.timeLimit > 0) {
            sb.append("--time-limit ");
            long seconds = TimeUnit.SECONDS.convert(options.timeLimit, options.timeLimitUnits);
            if (seconds > 180) {
                seconds = 180;
            }
            sb.append(seconds);
            sb.append(' ');
        }

        sb.append(remoteFilePath);

        return sb.toString();
    }

    @Override
    public void executeShellCommand(IShellOutputReceiver receiver, String command, String... args)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), this,
                receiver, DdmPreferences.getTimeOut(), command, args);
    }


    @Override
    public void executeShellCommand(IShellOutputReceiver receiver,
                                    int maxTimeToOutputResponse, String command, String... args)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), this,
                receiver, maxTimeToOutputResponse, command, args);
    }

    @Override
    public void executeShellCommand(IShellOutputReceiver receiver,
                                    long maxTimeToOutputResponse, TimeUnit maxTimeUnits, String command, String... args)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), this,
                receiver, maxTimeToOutputResponse, maxTimeUnits, command, args);
    }


    @Override
    public void createForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort),     //$NON-NLS-1$
                String.format("tcp:%d", remotePort));   //$NON-NLS-1$
    }

    @Override
    public void createForward(int localPort, String remoteSocketName,
                              DeviceUnixSocketNamespace namespace) throws TimeoutException,
            AdbCommandRejectedException, IOException {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort),     //$NON-NLS-1$
                String.format("%s:%s", namespace.getType(), remoteSocketName));   //$NON-NLS-1$
    }

    @Override
    public void removeForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort),     //$NON-NLS-1$
                String.format("tcp:%d", remotePort));   //$NON-NLS-1$
    }

    @Override
    public void removeForward(int localPort, String remoteSocketName,
                              DeviceUnixSocketNamespace namespace) throws TimeoutException,
            AdbCommandRejectedException, IOException {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this,
                String.format("tcp:%d", localPort),     //$NON-NLS-1$
                String.format("%s:%s", namespace.getType(), remoteSocketName));   //$NON-NLS-1$
    }

    Device(DeviceMonitor monitor, String serialNumber, DeviceState deviceState) {
        mMonitor = monitor;
        mSerialNumber = serialNumber;
        mState = deviceState;
    }

    DeviceMonitor getMonitor() {
        return mMonitor;
    }

    void update(int changeMask) {
        mMonitor.getServer().deviceChanged(this, changeMask);
    }


    void setMountingPoint(String name, String value) {
        mMountPoints.put(name, value);
    }

    @Override
    public void pushFile(String local, String remote)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        SyncService sync = null;
        try {
            String targetFileName = getFileName(local);

            Log.d(targetFileName, String.format("Uploading %1$s onto device '%2$s'",
                    targetFileName, getSerialNumber()));

            sync = getSyncService();
            if (sync != null) {
                String message = String.format("Uploading file onto device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pushFile(local, remote, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } finally {
            if (sync != null) {
                sync.close();
            }
        }
    }

    @Override
    public void pullFile(String remote, String local)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        SyncService sync = null;
        try {
            String targetFileName = getFileName(remote);

            Log.d(targetFileName, String.format("Downloading %1$s from device '%2$s'",
                    targetFileName, getSerialNumber()));

            sync = getSyncService();
            if (sync != null) {
                String message = String.format("Downloading file from device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pullFile(remote, local, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } finally {
            if (sync != null) {
                sync.close();
            }
        }
    }

    @Override
    public String installPackage(String packageFilePath, boolean reinstall,
                                 String... extraArgs)
            throws InstallException {
        try {
            String remoteFilePath = syncPackageToDevice(packageFilePath);
            String result = installRemotePackage(remoteFilePath, reinstall, extraArgs);
            removeRemotePackage(remoteFilePath);
            return result;
        } catch (IOException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (SyncException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void installPackages(List<String> apkFilePaths, int timeOutInMs, boolean reinstall,
                                String... extraArgs) throws InstallException {

        assert (!apkFilePaths.isEmpty());
        if (getApiLevel() < 21) {
            Log.w("Internal error : installPackages invoked with device < 21 for %s",
                    Joiner.on(",").join(apkFilePaths));

            if (apkFilePaths.size() == 1) {
                installPackage(apkFilePaths.get(0), reinstall, extraArgs);
                return;
            }
            Log.e("Internal error : installPackages invoked with device < 21 for multiple APK : %s",
                    Joiner.on(",").join(apkFilePaths));
            throw new InstallException(
                    "Internal error : installPackages invoked with device < 21 for multiple APK : "
                            + Joiner.on(",").join(apkFilePaths));
        }
        String mainPackageFilePath = apkFilePaths.get(0);
        Log.d(mainPackageFilePath,
                String.format("Uploading main %1$s and %2$s split APKs onto device '%3$s'",
                        mainPackageFilePath, Joiner.on(',').join(apkFilePaths),
                        getSerialNumber()));

        try {
            // create a installation session.

            List<String> extraArgsList = extraArgs != null
                    ? ImmutableList.copyOf(extraArgs)
                    : ImmutableList.<String>of();

            String sessionId = createMultiInstallSession(apkFilePaths, extraArgsList, reinstall);
            if (sessionId == null) {
                Log.d(mainPackageFilePath, "Failed to establish session, quit installation");
                throw new InstallException("Failed to establish session");
            }
            Log.d(mainPackageFilePath, String.format("Established session id=%1$s", sessionId));

            // now upload each APK in turn.
            int index = 0;
            boolean allUploadSucceeded = true;
            while (allUploadSucceeded && index < apkFilePaths.size()) {
                allUploadSucceeded = uploadAPK(sessionId, apkFilePaths.get(index), index++);
            }

            // if all files were upload successfully, commit otherwise abandon the installation.
            String command = allUploadSucceeded
                    ? "pm install-commit " + sessionId
                    : "pm install-abandon " + sessionId;
            InstallReceiver receiver = new InstallReceiver();
            executeShellCommand(receiver, timeOutInMs, TimeUnit.MILLISECONDS, command);
            String errorMessage = receiver.getErrorMessage();
            if (errorMessage != null) {
                String message = String.format("Failed to finalize session : %1$s", errorMessage);
                Log.e(mainPackageFilePath, message);
                throw new InstallException(message);
            }
            // in case not all files were upload and we abandoned the install, make sure to
            // notifier callers.
            if (!allUploadSucceeded) {
                throw new InstallException("Unable to upload some APKs");
            }
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw new InstallException(e);

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw new InstallException(e);

        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            Log.e(LOG_TAG, String.format("Error during shell execution: %1$s", e.getMessage()));
            throw new InstallException(e);
        }
    }

    /**
     * Implementation of {@link MultiLineReceiver} that can receive a
     * Success message from ADB followed by a session ID.
     */
    private static class MultiInstallReceiver extends MultiLineReceiver {

        private static final Pattern successPattern = Pattern.compile("Success: .*\\[(\\d*)\\]");

        @Nullable
        String sessionId = null;

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                Matcher matcher = successPattern.matcher(line);
                if (matcher.matches()) {
                    sessionId = matcher.group(1);
                }
            }

        }

        @Nullable
        public String getSessionId() {
            return sessionId;
        }
    }

    @Nullable
    private String createMultiInstallSession(List<String> apkFileNames,
                                             @NonNull Collection<String> extraArgs, boolean reinstall)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {

        List<File> apkFiles = Lists.transform(apkFileNames, new Function<String, File>() {
            @Override
            public File apply(String input) {
                return new File(input);
            }
        });

        long totalFileSize = 0L;
        for (File apkFile : apkFiles) {
            if (apkFile.exists() && apkFile.isFile()) {
                totalFileSize += apkFile.length();
            } else {
                throw new IllegalArgumentException(apkFile.getAbsolutePath() + " is not a file");
            }
        }
        StringBuilder parameters = new StringBuilder();
        if (reinstall) {
            parameters.append(("-r "));
        }
        parameters.append(Joiner.on(' ').join(extraArgs));
        MultiInstallReceiver receiver = new MultiInstallReceiver();
        String cmd = String.format("pm install-create %1$s -S %2$d",
                parameters.toString(),
                totalFileSize);
        executeShellCommand(receiver, DdmPreferences.getTimeOut(), cmd);
        return receiver.getSessionId();
    }

    private static final CharMatcher UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS =
            CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'))
                    .or(CharMatcher.anyOf("_-")).negate();

    private boolean uploadAPK(final String sessionId, String apkFilePath, int uniqueId) {
        Log.d(sessionId, String.format("Uploading APK %1$s ", apkFilePath));
        File fileToUpload = new File(apkFilePath);
        if (!fileToUpload.exists()) {
            Log.e(sessionId, String.format("File not found: %1$s", apkFilePath));
            return false;
        }
        if (fileToUpload.isDirectory()) {
            Log.e(sessionId, String.format("Directory upload not supported: %1$s", apkFilePath));
            return false;
        }
        String baseName = fileToUpload.getName().lastIndexOf('.') != -1
                ? fileToUpload.getName().substring(0, fileToUpload.getName().lastIndexOf('.'))
                : fileToUpload.getName();

        baseName = UNSAFE_PM_INSTALL_SESSION_SPLIT_NAME_CHARS.replaceFrom(baseName, '_');

        String command = String.format("pm install-write -S %d %s %d_%s -",
                fileToUpload.length(), sessionId, uniqueId, baseName);

        Log.d(sessionId, String.format("Executing : %1$s", command));
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(fileToUpload));
            InstallReceiver receiver = new InstallReceiver();
            AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(),
                    AdbHelper.AdbService.EXEC, this,
                    receiver, DdmPreferences.getTimeOut(), TimeUnit.MILLISECONDS, inputStream, command);
            if (receiver.getErrorMessage() != null) {
                Log.e(sessionId, String.format("Error while uploading %1$s : %2$s", fileToUpload.getName(),
                        receiver.getErrorMessage()));
            } else {
                Log.d(sessionId, String.format("Successfully uploaded %1$s", fileToUpload.getName()));
            }
            return receiver.getErrorMessage() == null;
        } catch (Exception e) {
            Log.e(sessionId, e);
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(sessionId, e);
                }
            }

        }
    }

    @Override
    public String syncPackageToDevice(String localFilePath)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        SyncService sync = null;
        try {
            String packageFileName = getFileName(localFilePath);
            String remoteFilePath = String.format("/data/local/tmp/%1$s", packageFileName); //$NON-NLS-1$

            Log.d(packageFileName, String.format("Uploading %1$s onto device '%2$s'",
                    packageFileName, getSerialNumber()));

            sync = getSyncService();
            if (sync != null) {
                String message = String.format("Uploading file onto device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pushFile(localFilePath, remoteFilePath, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
            return remoteFilePath;
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } finally {
            if (sync != null) {
                sync.close();
            }
        }
    }

    /**
     * Helper method to retrieve the file name given a local file path
     *
     * @param filePath full directory path to file
     * @return {@link String} file name
     */
    private static String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    @Override
    public String installRemotePackage(String remoteFilePath, boolean reinstall,
                                       String... extraArgs) throws InstallException {
        try {
            InstallReceiver receiver = new InstallReceiver();
            StringBuilder optionString = new StringBuilder();
            if (reinstall) {
                optionString.append("-r ");
            }
            if (extraArgs != null) {
                optionString.append(Joiner.on(' ').join(extraArgs));
            }
            String cmd = String.format("pm install %1$s \"%2$s\"", optionString.toString(),
                    remoteFilePath);
            executeShellCommand(receiver, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES, cmd);
            return receiver.getErrorMessage();
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        } catch (IOException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public void removeRemotePackage(String remoteFilePath) throws InstallException {
        try {
            executeShellCommand(
                    new NullOutputReceiver(), INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES, String.format("rm \"%1$s\"", remoteFilePath));
        } catch (IOException e) {
            throw new InstallException(e);
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        }
    }

    @Override
    public String uninstallPackage(String packageName) throws InstallException {
        try {
            InstallReceiver receiver = new InstallReceiver();
            executeShellCommand(receiver, INSTALL_TIMEOUT_MINUTES,
                    TimeUnit.MINUTES, "pm uninstall " + packageName);
            return receiver.getErrorMessage();
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        } catch (IOException e) {
            throw new InstallException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.adb.IDevice#reboot()
     */
    @Override
    public void reboot(String into)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.reboot(into, AndroidDebugBridge.getSocketAddress(), this);
    }

    @Override
    public Integer getBatteryLevel() throws TimeoutException, AdbCommandRejectedException,
            IOException, ShellCommandUnresponsiveException {
        // use default of 5 minutes
        return getBatteryLevel(5 * 60 * 1000);
    }

    @Override
    public Integer getBatteryLevel(long freshnessMs) throws TimeoutException,
            AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException {
        Future<Integer> futureBattery = getBattery(freshnessMs, TimeUnit.MILLISECONDS);
        try {
            return futureBattery.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    @Override
    public Future<Integer> getBattery() {
        return getBattery(5, TimeUnit.MINUTES);
    }

    @NonNull
    @Override
    public Future<Integer> getBattery(long freshnessTime, @NonNull TimeUnit timeUnit) {
        return mBatteryFetcher.getBattery(freshnessTime, timeUnit);
    }

    @NonNull
    @Override
    public List<String> getAbis() {
        /* Try abiList (implemented in L onwards) otherwise fall back to abi and abi2. */
        String abiList = getProperty(IDevice.PROP_DEVICE_CPU_ABI_LIST);
        if (abiList != null) {
            return Lists.newArrayList(abiList.split(","));
        } else {
            List<String> abis = Lists.newArrayListWithExpectedSize(2);
            String abi = getProperty(IDevice.PROP_DEVICE_CPU_ABI);
            if (abi != null) {
                abis.add(abi);
            }

            abi = getProperty(IDevice.PROP_DEVICE_CPU_ABI2);
            if (abi != null) {
                abis.add(abi);
            }

            return abis;
        }
    }

    @Override
    public int getDensity() {
        String densityValue = getProperty(IDevice.PROP_DEVICE_DENSITY);
        if (densityValue != null) {
            try {
                return Integer.parseInt(densityValue);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return -1;
    }

    @Override
    public String getLanguage() {
        return getProperties().get(IDevice.PROP_DEVICE_LANGUAGE);
    }

    @Override
    public String getRegion() {
        return getProperty(IDevice.PROP_DEVICE_REGION);
    }


    private InputManager inputManager;

    public InputManager getInputManager() {
        if (inputManager == null) {
            try {
                inputManager = new InputManager(this);
            } catch (ExecutionException|InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG,"Create inputManager error");
            }
        }
        return inputManager;
    }

    private HashMap<Type, IRemoteController> controlerHashMap = new HashMap<>();

    @Override
    public IRemoteController getRemoteControler(Type type) {
        IRemoteController controler = controlerHashMap.get(type);
        if (controler != null) {
        } else {
            switch (type) {
                case MONKEY:
                    controler = new MonkeyTransport(1080, this);
                    controler.create();
                    break;
                default:
                    controler = null;
            }
            controlerHashMap.put(type, controler);
        }
        return controler;
    }

    @Override
    public void onShutDown() {
        Log.d(LOG_TAG, "ShutDown!!" + getName());
        inputManager.onShutDown();
    }
}
