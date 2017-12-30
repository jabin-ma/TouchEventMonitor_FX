package com.android.ddmlib.input;

import com.android.ddmlib.adb.IDevice;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputManager {
    private static final String TAG = "InputManager";
    private static final boolean DEBUG = false;
    private IDevice mRemoteDevice;
    //从驱动文件读取原始事件
    private EventHub eventHub;
    //将原始事件转换为本地事件
    private EventHubReader eventHubReader;
    //驱动event pool
    private MappedEventDispatcher mappedEventDispatcher;
    private ExecutorService mThreads = Executors.newCachedThreadPool();

    public InputManager(IDevice mRemoteDevice) throws ExecutionException, InterruptedException {
        this.mRemoteDevice = mRemoteDevice;
        init(new EventHub(this));
    }


    /**
     * 初始化manager
     */
    void init(EventHub eventHub) {
        this.eventHub = eventHub;
        eventHubReader = new EventHubReader(this.eventHub);
        mappedEventDispatcher = new MappedEventDispatcher(eventHubReader);
        mThreads.submit(eventHubReader);
        mThreads.submit(mappedEventDispatcher);
    }


    /**
     * 远程设备,即手机
     *
     * @return
     */
    public IDevice getRemoteDevice() {
        return mRemoteDevice;
    }

    /**
     * 设置监听器，当事件被解析完成时，会调用该监听器
     *
     * @param listener
     * @return 添加是否成功
     */
    public boolean addOnTouchEventListener(OnTouchEventListener listener) {
        return mappedEventDispatcher.addOnTouchEventListener(listener);
    }

    /**
     * 解除注册
     *
     * @param listener
     * @return 解除是否成功
     */
    public boolean unregisterTouchEventListener(OnTouchEventListener listener) {
        return mappedEventDispatcher.unregisterTouchEventListener(listener);
    }

    /**
     * 不建议手动调用
     */
    public void onShutDown() {
        eventHub.quit();
        mThreads.shutdownNow();
        mThreads = null;
        eventHub = null;
        mRemoteDevice = null;
        eventHubReader = null;
        mappedEventDispatcher = null;
    }
}
