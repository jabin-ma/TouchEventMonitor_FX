package com.android.ddmlib.input;

import com.android.ddmlib.IDevice;

import java.util.ArrayList;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputManager {
    private IDevice mAndroidDevice;
    //从驱动文件读取原始事件
    private EventHub eventHub;
    //将原始事件转换为本地事件
    private InputReader inputReader;
    //从eventhub读取event
    private InputReaderThread inputReaderThread;
    //待分发的event pool
    private EventPool eventPool;
    //驱动event pool
    private InputDispatcherThread inputDispatcherThread;

    public InputManager(IDevice mAndroidDevice) {
        this.mAndroidDevice = mAndroidDevice;
        eventHub = new EventHub(this);
        inputReader = new InputReader(eventHub);
        eventPool = new EventPool();
        inputReaderThread = new InputReaderThread(inputReader, eventPool);
        inputDispatcherThread = new InputDispatcherThread(eventPool);
        inputReaderThread.start();
        inputDispatcherThread.start();
    }


    public IDevice getAndroidDevice() {
        return mAndroidDevice;
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

    public void onShutDown() {
        eventHub.quit();
        try {
            inputReaderThread.interrupt();
            inputReaderThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            inputDispatcherThread.interrupt();
            inputDispatcherThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
