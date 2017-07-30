package com.android.ddmlib.input;

import com.android.ddmlib.IDevice;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputManager {
    private IDevice mAndroidDevice;
    //从驱动文件读取原始事件
    private EventHub eventHub;
    //将原始事件转换为本地事件
    private EventHubReader eventHubReader;

    //驱动event pool
    private MappedEventDispatcher mappedEventDispatcher;
    ExecutorService mThreads = Executors.newCachedThreadPool();

    public InputManager(IDevice mAndroidDevice) {
        this.mAndroidDevice = mAndroidDevice;
        eventHub = new EventHub(this);
        eventHubReader = new EventHubReader(eventHub);
        mappedEventDispatcher = new MappedEventDispatcher(eventHubReader);
        mThreads.submit(eventHubReader);
        mThreads.submit(mappedEventDispatcher);
    }


    public IDevice getAndroidDevice() {
        return mAndroidDevice;
    }

    public ArrayList<InputDevice> getDevices() {
        return eventHub.getDevices();
    }


    public boolean addOnTouchEventListener(OnTouchEventListener listener) {
        return mappedEventDispatcher.addOnTouchEventListener(listener);
    }


    public boolean unregisterTouchEventListener(OnTouchEventListener listener) {
        return mappedEventDispatcher.unregisterTouchEventListener(listener);
    }

    public void onShutDown() {
        eventHub.quit();
        mThreads.shutdownNow();
        mThreads=null;
        eventHub=null;
        mAndroidDevice=null;
        eventHubReader=null;
        mappedEventDispatcher=null;
    }
}
