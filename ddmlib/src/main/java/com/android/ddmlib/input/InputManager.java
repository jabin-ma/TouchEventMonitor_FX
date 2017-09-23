package com.android.ddmlib.input;

import com.android.ddmlib.adb.*;
import com.android.ddmlib.adb.TimeoutException;
import com.android.ddmlib.utils.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.concurrent.*;

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
    /*
     * 获取设备超时时间
     */
    private static final int TIMEOUT_SEC = 2;
    /*
     * 所有输入设备
     */
    private HashMap<String, InputDevice> mDevices = new HashMap<>();

    public InputManager(IDevice mRemoteDevice) throws ExecutionException, InterruptedException {
        this.mRemoteDevice = mRemoteDevice;
        List<InputDevice> devs=mThreads.submit(new ScanDeviceTask()).get();
        for (InputDevice dev:devs){
            if (!mDevices.containsKey(dev.getDevFile())) {
                mDevices.put(dev.getDevFile(), dev);
            }
        }
        init(new EventHub(this));
    }


    /**
     * 初始化manager
     */
    public void init(EventHub eventHub){
        this.eventHub = eventHub;
        eventHubReader = new EventHubReader(this.eventHub);
        mappedEventDispatcher = new MappedEventDispatcher(eventHubReader);
        mThreads.submit(eventHubReader);
        mThreads.submit(mappedEventDispatcher);
    }



    public IDevice getRemoteDevice() {
        return mRemoteDevice;
    }

    public ArrayList<InputDevice> getDevices() {
        return new ArrayList<>(mDevices.values());
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
        mRemoteDevice =null;
        eventHubReader=null;
        mappedEventDispatcher=null;
    }

    class ScanDeviceTask implements Callable<List<InputDevice>> {
        @Override
        public List<InputDevice>  call() throws Exception {
            try {
                List<InputDevice> temp=new ArrayList<>();
                getRemoteDevice().executeShellCommand(new SingleLineReceiver() {
                    ArrayList<String> sb = new ArrayList<>();
                    @Override
                    public void processNewLines(String line) {
                        if (DEBUG) Log.d(getClass().getName(), line);
                        if (line.startsWith("add device")) {
                            if (sb.size() > 0) {
                                temp.add(new InputDevice((List<String>) sb.clone()));
                                sb.clear();
                            }
                        }
                        sb.add(line.trim());
                    }

                    @Override
                    public void done() {
                        temp.add(new InputDevice((List<String>) sb.clone()));
                        sb.clear();
                    }
                }, TIMEOUT_SEC, TimeUnit.SECONDS, Command.GETEVENT_GETDEVICE);
                return temp;
            } catch (IOException | TimeoutException | ShellCommandUnresponsiveException | AdbCommandRejectedException e) {
                e.printStackTrace();
            } finally {
              Log.d(TAG,"ScanDevice...the end");
            }
            return null;
        }
    }
}
