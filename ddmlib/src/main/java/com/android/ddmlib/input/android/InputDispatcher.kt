package com.android.ddmlib.input.android

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by majipeng on 2017/6/19.
 */
class InputDispatcher(inputManager: InputManager) {
    private val waitingForDispatch = LinkedBlockingQueue<MonitorEvent>(2)

    internal fun dispatchEvent(event: MonitorEvent) {
        waitingForDispatch.add(event)
    }

     val waitingForDispatchEvent: MonitorEvent
        @Throws(InterruptedException::class)
        get() = waitingForDispatch.take()
}
