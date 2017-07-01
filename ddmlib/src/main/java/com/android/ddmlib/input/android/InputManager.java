package com.android.ddmlib.input.android;

import com.android.ddmlib.IDevice;

import java.util.ArrayList;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputManager {
    IDevice mAndroidDevice;
    EventHub eventHub;
    InputReader inputReader;

    InputReaderThread inputReaderThread;
    InputDispatcher inputDispatcher;
    InputDispatcherThread inputDispatcherThread;

    public InputManager(IDevice mAndroidDevice) {
        this.mAndroidDevice = mAndroidDevice;
        eventHub = new EventHub(this);
        inputReader = new InputReader(eventHub);
        inputReaderThread = new InputReaderThread(this);
        inputDispatcher = new InputDispatcher(this);
        inputDispatcherThread = new InputDispatcherThread(this);

        inputReaderThread.start();
        inputDispatcherThread.start();
    }


    public IDevice getAndroidDevice() {
        return mAndroidDevice;
    }

    EventHub getEventHub() {
        return eventHub;
    }

    InputReader getInputReader() {
        return inputReader;
    }

    public InputDispatcher getInputDispatcher() {
        return inputDispatcher;
    }

    public ArrayList<InputDevice> getDevices() {
        return eventHub.getDevices();
    }


    public boolean addOnTouchEventListener(OnTouchEventListener listener) {
        return inputDispatcherThread.addOnTouchEventListener(listener);
    }


    public boolean unregisterTouchEventListener(OnTouchEventListener listener) {
        return inputDispatcherThread.unregisterTouchEventListener(listener);
    }


}
