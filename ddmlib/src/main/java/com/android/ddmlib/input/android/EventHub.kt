package com.android.ddmlib.input.android

import com.android.ddmlib.*
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class EventHub(private val mContext: InputManager) {

    private val executorService = Executors.newCachedThreadPool()

    private val rawEvents = LinkedBlockingQueue<RawEvent>()

    private val futureHashMap = HashMap<String, Future<*>>()

    private val mDevices = HashMap<String, InputDevice>()

    init {
        scanDevice(true)
    }

    fun scanDevice(force: Boolean) {
        if (force || mDevices.isEmpty()) {
            try {
                mContext.androidDevice.executeShellCommand(object : OneLineReceiver() {
                    internal var sb = ArrayList<String>()
                    override fun processNewLines(line: String) {
                        if (DEBUG) Log.d(javaClass.name, line)
                        if (line.startsWith("add device")) {
                            if (sb.size > 0) {
                                putDevice(sb)
                                sb.clear()
                            }
                        }
                        sb.add(line.trim { it <= ' ' })
                        //                    if(DEBUG)Log.d(getClass().getName(), sb.toString());
                    }

                    override fun done() {
                        putDevice(sb)
                        sb.clear()
                    }
                }, TIMEOUT_SEC, TimeUnit.SECONDS, Command.GETEVENT_GETDEVICE)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: AdbCommandRejectedException) {
                e.printStackTrace()
            } catch (e: ShellCommandUnresponsiveException) {
                //            e.printStackTrace();
            } catch (e: TimeoutException) {
                //            e.printStackTrace();
            } finally {

            }
        }
        //        result.add(new InputDevice((List<String>) sb.clone(), dev));
        //        return new ArrayList<>(devList.values());
    }

    fun putDevice(sb: ArrayList<String>) {
        val tempDev = InputDevice(sb.clone() as List<String>)
        if (!mDevices.containsKey(tempDev.devFile)) {
            mDevices.put(tempDev.devFile!!, tempDev)
            addDevice(tempDev)
        }
    }

    fun addDevice(tempDev: InputDevice) {
        if (futureHashMap.containsKey(tempDev.devFile)) {
            if (futureHashMap[tempDev.devFile]?.isDone()!!) {

            } else {
                return
            }
        }

        val submitFuture = executorService.submit<Void> {
            try {
                mContext.androidDevice.executeShellCommand(object : OneLineReceiver() {
                    override fun processNewLines(line: String) {
                        val rawEvent = RawEvent(line, tempDev.devFile)
                        rawEvents.add(rawEvent)
                    }
                }, -1, Command.GETEVENT_WHATCH_TEXT_EVENT, tempDev.devFile!!)

            } catch (e: TimeoutException) {
                e.printStackTrace()
            } catch (e: AdbCommandRejectedException) {
                e.printStackTrace()
            } catch (e: ShellCommandUnresponsiveException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            null
        }
        futureHashMap.put(tempDev.devFile!!, submitFuture)
    }


    fun removeMonitorDevice(): Boolean {
        return false
    }

    val event: RawEvent
        @Throws(InterruptedException::class)
        get() = rawEvents.take()


    val devices: ArrayList<InputDevice>
        get() = ArrayList(mDevices.values)

    companion object {


        //    private LinkedHashMap<String>

        //    private IDevice mAndroidDevice;

        private val TAG = "EventHub"
        private val DEBUG = false

        private val TIMEOUT_SEC = 2L
    }
}
