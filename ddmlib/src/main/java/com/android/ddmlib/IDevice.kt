/*
 * Copyright (C) 2008 The Android Open Source Project
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
import com.android.ddmlib.input.android.Command
import com.android.ddmlib.input.android.InputManager
import com.android.ddmlib.log.LogReceiver
import com.android.ddmlib.monkey.NetworkMonkey

import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * A Device. It can be a physical device or an emulator.
 */
interface IDevice : IShellEnabledDevice {

    /**
     * Device level software features.
     */
    enum class Feature {
        SCREEN_RECORD, // screen recorder available?
        PROCSTATS
        // procstats service (dumpsys procstats) available
    }

    /**
     * Device level hardware features.
     */
    enum class HardwareFeature private constructor(val characteristic: String) {
        WATCH("watch"),
        TV("tv")
    }

    /**
     * The state of a device.
     */
    enum class DeviceState private constructor(//$NON-NLS-1$

            private val mState: String) {
        BOOTLOADER("bootloader"), //$NON-NLS-1$
        OFFLINE("offline"), //$NON-NLS-1$
        ONLINE("device"), //$NON-NLS-1$
        RECOVERY("recovery"), //$NON-NLS-1$
        UNAUTHORIZED("unauthorized");


        companion object {

            /**
             * Returns a [DeviceState] from the string returned by `adb devices`.

             * @param state the device state.
             * *
             * @return a [DeviceState] object or `null` if the state is unknown.
             */
            @Nullable
            fun getState(state: String): DeviceState? {
                for (deviceState in values()) {
                    if (deviceState.mState == state) {
                        return deviceState
                    }
                }
                return null
            }
        }
    }

    /**
     * Namespace of a Unix Domain Socket created on the device.
     */
    enum class DeviceUnixSocketNamespace private constructor(//$NON-NLS-1$

            internal val type: String) {
        ABSTRACT("localabstract"), //$NON-NLS-1$
        FILESYSTEM("localfilesystem"), //$NON-NLS-1$
        RESERVED("localreserved")
    }

    /**
     * Returns the serial number of the device.
     */
    @get:NonNull
    val serialNumber: String

    /**
     * Returns the name of the AVD the emulator is running.
     *
     * This is only valid if [.isEmulator] returns true.
     *
     * If the emulator is not running any AVD (for instance it's running from an Android source
     * tree build), this method will return "`<build>`".

     * @return the name of the AVD or `null` if there isn't any.
     */
    @get:Nullable
    val avdName: String?

    /**
     * Returns the state of the device.
     */
    val state: DeviceState?

    /**
     * Returns the cached device properties. It contains the whole output of 'getprop'

     */
    
    @get:Deprecated("use {@link #getSystemProperty(String)} instead")
    val properties: Map<String, String>

    /**
     * Returns the number of property for this device.

     */
    
    @get:Deprecated("implementation detail")
    val propertyCount: Int

    /**
     * Convenience method that attempts to retrieve a property via
     * [.getSystemProperty] with minimal wait time, and swallows exceptions.

     * @param name the name of the value to return.
     * *
     * @return the value or `null` if the property value was not immediately available
     */
    @Nullable
    fun getProperty(@NonNull name: String): String?

    /**
     * Returns `true>` if properties have been cached
     */
    fun arePropertiesSet(): Boolean

    /**
     * A variant of [.getProperty] that will attempt to retrieve the given
     * property from device directly, without using cache.
     * This method should (only) be used for any volatile properties.

     * @param name the name of the value to return.
     * *
     * @return the value or `null` if the property does not exist
     * *
     * @throws TimeoutException                  in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException       if adb rejects the command
     * *
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a
     * *                                           given time.
     * *
     * @throws IOException                       in case of I/O error on the connection.
     * *
     */
    @Deprecated("use {@link #getSystemProperty(String)}")
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    fun getPropertySync(name: String): String?

    /**
     * A combination of [.getProperty] and [.getPropertySync] that
     * will attempt to retrieve the property from cache. If not found, will synchronously
     * attempt to query device directly and repopulate the cache if successful.

     * @param name the name of the value to return.
     * *
     * @return the value or `null` if the property does not exist
     * *
     * @throws TimeoutException                  in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException       if adb rejects the command
     * *
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a
     * *                                           given time.
     * *
     * @throws IOException                       in case of I/O error on the connection.
     * *
     */
    @Deprecated("use {@link #getSystemProperty(String)} instead")
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    fun getPropertyCacheOrSync(name: String): String?

    /**
     * Returns whether this device supports the given software feature.
     */
    fun supportsFeature(@NonNull feature: Feature): Boolean

    /**
     * Returns whether this device supports the given hardware feature.
     */
    fun supportsFeature(@NonNull feature: HardwareFeature): Boolean

    /**
     * Returns a mount point.

     * @param name the name of the mount point to return
     * *
     * @see .MNT_EXTERNAL_STORAGE

     * @see .MNT_ROOT

     * @see .MNT_DATA
     */
    @Nullable
    fun getMountPoint(@NonNull name: String): String?

    /**
     * Returns if the device is ready.

     * @return `true` if [.getState] returns [DeviceState.ONLINE].
     */
    val isOnline: Boolean

    /**
     * Returns `true` if the device is an emulator.
     */
    val isEmulator: Boolean

    /**
     * Returns if the device is offline.

     * @return `true` if [.getState] returns [DeviceState.OFFLINE].
     */
    val isOffline: Boolean

    /**
     * Returns if the device is in bootloader mode.

     * @return `true` if [.getState] returns [DeviceState.BOOTLOADER].
     */
    val isBootLoader: Boolean

    /**
     * Returns whether the [Device] has [Client]s.
     */
    fun hasClients(): Boolean

    /**
     * Returns the array of clients.
     */
    val clients: Array<Client>

    /**
     * Returns a [Client] by its application name.

     * @param applicationName the name of the application
     * *
     * @return the `Client` object or `null` if no match was found.
     */
    fun getClient(applicationName: String): Client?

    /**
     * Returns a [SyncService] object to push / pull files to and from the device.

     * @return `null` if the SyncService couldn't be created. This can happen if adb
     * * refuse to open the connection because the [IDevice] is invalid
     * * (or got disconnected).
     * *
     * @throws TimeoutException            in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 if the connection with adb failed.
     */
    val syncService: SyncService?

    /**
     * Returns a [FileListingService] for this device.
     */
    val fileListingService: FileListingService

    /**
     * Takes a screen shot of the device and returns it as a [RawImage].

     * @return the screenshot as a `RawImage` or `null` if something
     * * went wrong.
     * *
     * @throws TimeoutException            in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 in case of I/O error on the connection.
     */
    val screenshot: RawImage

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    fun getScreenshot(timeout: Long, unit: TimeUnit): RawImage

    /**
     * Initiates screen recording on the device if the device supports [Feature.SCREEN_RECORD].
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class, ShellCommandUnresponsiveException::class)
    fun startScreenRecorder(@NonNull remoteFilePath: String,
                            @NonNull options: ScreenRecorderOptions, @NonNull receiver: IShellOutputReceiver)


    @Deprecated("Use {@link #executeShellCommand(IShellOutputReceiver, long, java.util.concurrent.TimeUnit, String, String...)}.")
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    fun executeShellCommand(receiver: IShellOutputReceiver,
                            maxTimeToOutputResponse: Int, command: String, vararg args: String)

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    fun executeShellCommand(receiver: IShellOutputReceiver,
                            maxTimeToOutputResponse: Int, command: Command, vararg args: String)

    /**
     * Executes a shell command on the device, and sends the result to a <var>receiver</var>
     *
     * This is similar to calling
     * `executeShellCommand(command, receiver, DdmPreferences.getTimeOut())`.

     * @param command  the shell command to execute
     * *
     * @param receiver the [IShellOutputReceiver] that will receives the output of the shell
     * *                 command
     * *
     * @throws TimeoutException                  in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException       if adb rejects the command
     * *
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output
     * *                                           for a given time.
     * *
     * @throws IOException                       in case of I/O error on the connection.
     * *
     * @see .executeShellCommand
     * @see DdmPreferences.getTimeOut
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    fun executeShellCommand(receiver: IShellOutputReceiver, command: String, vararg args: String)

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
    fun executeShellCommand(receiver: IShellOutputReceiver, command: Command, vararg args: String)

    /**
     * Runs the event log service and outputs the event log to the [LogReceiver].
     *
     * This call is blocking until [LogReceiver.isCancelled] returns true.

     * @param receiver the receiver to receive the event log entries.
     * *
     * @throws TimeoutException            in case of timeout on the connection. This can only be thrown if the
     * *                                     timeout happens during setup. Once logs start being received, no timeout will occur as it's
     * *                                     not possible to detect a difference between no log and timeout.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 in case of I/O error on the connection.
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    fun runEventLogService(receiver: LogReceiver)

    /**
     * Runs the log service for the given log and outputs the log to the [LogReceiver].
     *
     * This call is blocking until [LogReceiver.isCancelled] returns true.

     * @param logname  the logname of the log to read from.
     * *
     * @param receiver the receiver to receive the event log entries.
     * *
     * @throws TimeoutException            in case of timeout on the connection. This can only be thrown if the
     * *                                     timeout happens during setup. Once logs start being received, no timeout will
     * *                                     occur as it's not possible to detect a difference between no log and timeout.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 in case of I/O error on the connection.
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    fun runLogService(logname: String, receiver: LogReceiver)

    /**
     * Creates a port forwarding between a local and a remote port.

     * @param localPort  the local port to forward
     * *
     * @param remotePort the remote port.
     * *
     * @throws TimeoutException            in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 in case of I/O error on the connection.
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    fun createForward(localPort: Int, remotePort: Int)

    /**
     * Creates a port forwarding between a local TCP port and a remote Unix Domain Socket.

     * @param localPort        the local port to forward
     * *
     * @param remoteSocketName name of the unix domain socket created on the device
     * *
     * @param namespace        namespace in which the unix domain socket was created
     * *
     * @throws TimeoutException            in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 in case of I/O error on the connection.
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    fun createForward(localPort: Int, remoteSocketName: String,
                      namespace: DeviceUnixSocketNamespace)

    /**
     * Removes a port forwarding between a local and a remote port.

     * @param localPort  the local port to forward
     * *
     * @param remotePort the remote port.
     * *
     * @throws TimeoutException            in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 in case of I/O error on the connection.
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    fun removeForward(localPort: Int, remotePort: Int)

    /**
     * Removes an existing port forwarding between a local and a remote port.

     * @param localPort        the local port to forward
     * *
     * @param remoteSocketName the remote unix domain socket name.
     * *
     * @param namespace        namespace in which the unix domain socket was created
     * *
     * @throws TimeoutException            in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 in case of I/O error on the connection.
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    fun removeForward(localPort: Int, remoteSocketName: String,
                      namespace: DeviceUnixSocketNamespace)

    /**
     * Returns the name of the client by pid or `null` if pid is unknown

     * @param pid the pid of the client.
     */
    fun getClientName(pid: Int): String

    /**
     * Push a single file.

     * @param local  the local filepath.
     * *
     * @param remote The remote filepath.
     * *
     * @throws IOException                 in case of I/O error on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws TimeoutException            in case of a timeout reading responses from the device.
     * *
     * @throws SyncException               if file could not be pushed
     */
    @Throws(IOException::class, AdbCommandRejectedException::class, TimeoutException::class, SyncException::class)
    fun pushFile(local: String, remote: String)

    /**
     * Pulls a single file.

     * @param remote the full path to the remote file
     * *
     * @param local  The local destination.
     * *
     * @throws IOException                 in case of an IO exception.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws TimeoutException            in case of a timeout reading responses from the device.
     * *
     * @throws SyncException               in case of a sync exception.
     */
    @Throws(IOException::class, AdbCommandRejectedException::class, TimeoutException::class, SyncException::class)
    fun pullFile(remote: String, local: String)

    /**
     * Installs an Android application on device. This is a helper method that combines the
     * syncPackageToDevice, installRemotePackage, and removePackage steps

     * @param packageFilePath the absolute file system path to file on local host to install
     * *
     * @param reinstall       set to `true` if re-install of app should be performed
     * *
     * @param extraArgs       optional extra arguments to pass. See 'adb shell pm install --help' for
     * *                        available options.
     * *
     * @return a [String] with an error code, or `null` if success.
     * *
     * @throws InstallException if the installation fails.
     */
    @Throws(InstallException::class)
    fun installPackage(packageFilePath: String, reinstall: Boolean, vararg extraArgs: String): String?

    /**
     * Installs an Android application made of serveral APK files (one main and 0..n split packages)

     * @param apkFilePaths list of absolute file system path to files on local host to install
     * *
     * @param timeOutInMs
     * *
     * @param reinstall    set to `true` if re-install of app should be performed
     * *
     * @param extraArgs    optional extra arguments to pass. See 'adb shell pm install --help' for
     * *                     available options.
     * *
     * @throws InstallException if the installation fails.
     */

    @Throws(InstallException::class)
    fun installPackages(apkFilePaths: List<String>, timeOutInMs: Long,
                        reinstall: Boolean, vararg extraArgs: String)

    /**
     * Pushes a file to device

     * @param localFilePath the absolute path to file on local host
     * *
     * @return [String] destination path on device for file
     * *
     * @throws TimeoutException            in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException                 in case of I/O error on the connection.
     * *
     * @throws SyncException               if an error happens during the push of the package on the device.
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class, SyncException::class)
    fun syncPackageToDevice(localFilePath: String): String

    /**
     * Installs the application package that was pushed to a temporary location on the device.

     * @param remoteFilePath absolute file path to package file on device
     * *
     * @param reinstall      set to `true` if re-install of app should be performed
     * *
     * @param extraArgs      optional extra arguments to pass. See 'adb shell pm install --help' for
     * *                       available options.
     * *
     * @throws InstallException if the installation fails.
     */
    @Throws(InstallException::class)
    fun installRemotePackage(remoteFilePath: String, reinstall: Boolean,
                             vararg extraArgs: String): String?

    /**
     * Removes a file from device.

     * @param remoteFilePath path on device of file to remove
     * *
     * @throws InstallException if the installation fails.
     */
    @Throws(InstallException::class)
    fun removeRemotePackage(remoteFilePath: String)

    /**
     * Uninstalls an package from the device.

     * @param packageName the Android application package name to uninstall
     * *
     * @return a [String] with an error code, or `null` if success.
     * *
     * @throws InstallException if the uninstallation fails.
     */
    @Throws(InstallException::class)
    fun uninstallPackage(packageName: String): String?

    /**
     * Reboot the device.

     * @param into the bootloader name to reboot into, or null to just reboot the device.
     * *
     * @throws TimeoutException            in case of timeout on the connection.
     * *
     * @throws AdbCommandRejectedException if adb rejects the command
     * *
     * @throws IOException
     */
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class)
    fun reboot(into: String)

    /**
     * Return the device's battery level, from 0 to 100 percent.
     *
     *
     * The battery level may be cached. Only queries the device for its
     * battery level if 5 minutes have expired since the last successful query.

     * @return the battery level or `null` if it could not be retrieved
     * *
     */
    
    @get:Deprecated("use {@link #getBattery()}")
    val batteryLevel: Int?

    /**
     * Return the device's battery level, from 0 to 100 percent.
     *
     *
     * The battery level may be cached. Only queries the device for its
     * battery level if `freshnessMs` ms have expired since the last successful query.

     * @param freshnessMs
     * *
     * @return the battery level or `null` if it could not be retrieved
     * *
     * @throws ShellCommandUnresponsiveException
     * *
     */
    @Deprecated("use {@link #getBattery(long, TimeUnit))}")
    @Throws(TimeoutException::class, AdbCommandRejectedException::class, IOException::class, ShellCommandUnresponsiveException::class)
    fun getBatteryLevel(freshnessMs: Long): Int?

    /**
     * Return the device's battery level, from 0 to 100 percent.
     *
     *
     * The battery level may be cached. Only queries the device for its
     * battery level if 5 minutes have expired since the last successful query.

     * @return a [Future] that can be used to query the battery level. The Future will return
     * * a [ExecutionException] if battery level could not be retrieved.
     */
    @get:NonNull
    val battery: Future<Int>

    /**
     * Return the device's battery level, from 0 to 100 percent.
     *
     *
     * The battery level may be cached. Only queries the device for its
     * battery level if `freshnessTime` has expired since the last successful query.

     * @param freshnessTime the desired recency of battery level
     * *
     * @param timeUnit      the [TimeUnit] of freshnessTime
     * *
     * @return a [Future] that can be used to query the battery level. The Future will return
     * * a [ExecutionException] if battery level could not be retrieved.
     */
    @NonNull
    fun getBattery(freshnessTime: Long, @NonNull timeUnit: TimeUnit): Future<Int>


    /**
     * Returns the ABIs supported by this device. The ABIs are sorted in preferred order, with the
     * first ABI being the most preferred.

     * @return the list of ABIs.
     */
    @get:NonNull
    val abis: List<String>

    /**
     * Returns the density bucket of the device screen by reading the value for system property
     * [.PROP_DEVICE_DENSITY].

     * @return the density, or -1 if it cannot be determined.
     */
    val density: Int

    /**
     * Returns the user's language.

     * @return the user's language, or null if it's unknown
     */
    val language: String?

    /**
     * Returns the user's region.

     * @return the user's region, or null if it's unknown
     */
    val region: String?


    val monkey: NetworkMonkey


    val inputManager: InputManager?

    companion object {

        val PROP_BUILD_VERSION = "ro.build.version.release"
        val PROP_BUILD_API_LEVEL = "ro.build.version.sdk"
        val PROP_BUILD_CODENAME = "ro.build.version.codename"
        val PROP_BUILD_TAGS = "ro.build.tags"
        val PROP_BUILD_TYPE = "ro.build.type"
        val PROP_DEVICE_MODEL = "ro.product.model"
        val PROP_DEVICE_MANUFACTURER = "ro.product.manufacturer"
        val PROP_DEVICE_CPU_ABI_LIST = "ro.product.cpu.abilist"
        val PROP_DEVICE_CPU_ABI = "ro.product.cpu.abi"
        val PROP_DEVICE_CPU_ABI2 = "ro.product.cpu.abi2"
        val PROP_BUILD_CHARACTERISTICS = "ro.build.characteristics"
        val PROP_DEVICE_DENSITY = "ro.sf.lcd_density"
        val PROP_DEVICE_LANGUAGE = "persist.sys.language"
        val PROP_DEVICE_REGION = "persist.sys.country"

        val PROP_DEBUGGABLE = "ro.debuggable"

        /**
         * Serial number of the first connected emulator.
         */
        val FIRST_EMULATOR_SN = "emulator-5554" //$NON-NLS-1$
        /**
         * Device change bit mask: [DeviceState] change.
         */
        val CHANGE_STATE = 0x0001
        /**
         * Device change bit mask: [Client] list change.
         */
        val CHANGE_CLIENT_LIST = 0x0002
        /**
         * Device change bit mask: build info change.
         */
        val CHANGE_BUILD_INFO = 0x0004


        @Deprecated("Use {@link #PROP_BUILD_API_LEVEL}.")
        val PROP_BUILD_VERSION_NUMBER = PROP_BUILD_API_LEVEL

        val MNT_EXTERNAL_STORAGE = "EXTERNAL_STORAGE" //$NON-NLS-1$
        val MNT_ROOT = "ANDROID_ROOT" //$NON-NLS-1$
        val MNT_DATA = "ANDROID_DATA" //$NON-NLS-1$
    }

}