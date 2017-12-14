package com.android.ddmlib.input;

import com.android.ddmlib.adb.ShellCommandUnresponsiveException;
import com.android.ddmlib.adb.TimeoutException;
import com.android.ddmlib.adb.AdbCommandRejectedException;
import com.android.ddmlib.adb.SingleLineReceiver;
import com.android.ddmlib.utils.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

/**
 * 总线,负责从所有驱动文件中读出原始事件
 */
class EventHub {

    private static final String TAG = "EventHub";
    private static final boolean DEBUG = false;

    private static final int TIMEOUT_SEC = 2;

    private static final int OFFER_TIMEOUT=10 ;//10s
    /**
     *
     */
    private InputManager mContext;
    /**
     * 线程池，用于统一驱动管理Observer
     */
    private ExecutorService executorService = Executors.newCachedThreadPool();
    /**
     * 原始事件的队列
     */
    private BlockingQueue<IRawEvent> rawEventPool = new LinkedBlockingQueue<>();
    /**
     * 每个设备任务的句柄
     */
    private HashMap<String, Future> futureHashMap = new HashMap<>();

    public EventHub(InputManager inputManager,List<InputDevice> obDevices) {
        mContext = inputManager;
        for (InputDevice obDevice : obDevices) {
            if (futureHashMap.containsKey(obDevice.getDevFile())) {
                if (!futureHashMap.get(obDevice.getDevFile()).isDone()) {
                    continue;
                }
            }
            Future<Void> submitFuture = executorService.submit(new InputDeviceObserver(obDevice));
            futureHashMap.put(obDevice.getDevFile(), submitFuture);
        }
    }

    public EventHub(InputManager inputManager){
        this(inputManager,inputManager.getDevices());
    }


    /**
     * 获取一个原始事件进行消费,若当前没有可以消费的事件,那么该函数将会block
     * @return 返回null表示出错,否则将会返回待消费的事件
     */
    IRawEvent takeRawEvent() {
        if (executorService.isShutdown()) {
            return null;
        }
        try {
            return rawEventPool.take();
        } catch (InterruptedException e) {
            Log.d(TAG, "takeRawEvent error  will return null");
        }
        return null;
    }


    private void offerRawEvent(IRawEvent rawEvent)
    {
        try {
            rawEventPool.offer(rawEvent,OFFER_TIMEOUT,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.e(TAG,"offerRawEvent is timeout!!");
        }
    }

    /**
     * 关闭eventhub,通常在设备断线时调用
     */
    public void quit() {
        executorService.shutdownNow();
        rawEventPool.clear();
        Log.d(TAG, "quit.." + executorService);
    }

    /**
     * 读取底层事件
     */
    class InputDeviceObserver implements Callable<Void>{
        private InputDevice inputDevice;

        public InputDeviceObserver(InputDevice inputDevice) {
            this.inputDevice = inputDevice;
        }

        @Override
        public Void call() throws Exception {
            try {
                mContext.getRemoteDevice().executeShellCommand(new SingleLineReceiver() {
                    @Override
                    public void processNewLines(String line) {
                        IRawEvent rawEvent = new PlainTextRawEvent(line, inputDevice.getDevFile());
                        offerRawEvent(rawEvent);
                    }
                }, -1, Command.GETEVENT_WHATCH_TEXT_EVENT, inputDevice.getDevFile());

            } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException
                    | IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "stop :" + inputDevice.getDevFile());
            return null;
        }
    }
}
