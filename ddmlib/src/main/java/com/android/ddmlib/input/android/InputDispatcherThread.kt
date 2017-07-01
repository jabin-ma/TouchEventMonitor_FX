package com.android.ddmlib.input.android

import com.android.ddmlib.Log
import java.util.*

/**
 * Created by majipeng on 2017/6/19.
 */
class InputDispatcherThread(private val mContext: InputManager) : Thread() {

    private val listeners = ArrayList<OnTouchEventListener>()

    init {
        name = "InputDispatcher-Thread"
    }


    internal fun addOnTouchEventListener(listener: OnTouchEventListener): Boolean {
        return listeners.add(listener)
    }


    internal fun unregisterTouchEventListener(listener: OnTouchEventListener): Boolean {
        return listeners.remove(listener)
    }


    override fun run() {
        while (true) {
            try {
                val event = mContext.inputDispatcher.waitingForDispatchEvent
                Log.d("dispatch", event.inputDeviceProperty().toString() + "---->" + event)
                event.setDispatched()
                for (listener in listeners) {
                    listener.onTouchEvent(event)
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }
    }


}
