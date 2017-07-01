package com.android.ddmlib.input.android

/**
 * Created by majipeng on 2017/6/23.
 */
class InputReaderThread(private val mContext: InputManager) : Thread() {

    private val read = true

    init {
        name = "inputReader-Thread"
    }

    override fun run() {
        while (read) {

            val monitorEvent = mContext.inputReader.readBySync()
            if (monitorEvent != null) {
                mContext.inputDispatcher.dispatchEvent(monitorEvent)
            }
        }
    }
}
