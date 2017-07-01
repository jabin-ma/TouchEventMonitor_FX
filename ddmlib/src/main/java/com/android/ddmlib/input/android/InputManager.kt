package com.android.ddmlib.input.android

import com.android.ddmlib.IDevice

import java.util.ArrayList

/**
 * Created by majipeng on 2017/6/19.
 */
class InputManager(mAndroidDevice: IDevice) {
    var androidDevice: IDevice
        internal set
    internal var eventHub: EventHub
    internal var inputReader: InputReader

    internal var inputReaderThread: InputReaderThread
    var inputDispatcher: InputDispatcher
        internal set
    internal var inputDispatcherThread: InputDispatcherThread

    init {
        this.androidDevice = mAndroidDevice
        eventHub = EventHub(this)
        inputReader = InputReader(eventHub)
        inputReaderThread = InputReaderThread(this)
        inputDispatcher = InputDispatcher(this)
        inputDispatcherThread = InputDispatcherThread(this)

        inputReaderThread.start()
        inputDispatcherThread.start()
    }

    val devices: ArrayList<InputDevice>
        get() = eventHub.devices


    fun addOnTouchEventListener(listener: OnTouchEventListener): Boolean {
        return inputDispatcherThread.addOnTouchEventListener(listener)
    }

    fun unregisterTouchEventListener(listener: OnTouchEventListener): Boolean {
        return inputDispatcherThread.unregisterTouchEventListener(listener)
    }


}
