package com.android.ddmlib.input;

import com.android.ddmlib.adb.ShellCommandUnresponsiveException;
import com.android.ddmlib.adb.TimeoutException;
import com.android.ddmlib.adb.AdbCommandRejectedException;
import com.android.ddmlib.adb.SingleLineReceiver;
import com.android.ddmlib.utils.Log;

import java.io.IOException;
import java.util.ArrayList;
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

    private InputManager mContext;

    private ExecutorService executorService = Executors.newCachedThreadPool();

    private BlockingQueue<IRawEvent> rawEventPool = new LinkedBlockingQueue<>();

    private HashMap<String, Future> futureHashMap = new HashMap<>();

    public EventHub(InputManager inputManager,List<InputDevice> obDevices) {
        mContext = inputManager;
        for (InputDevice obDevice : obDevices) {
            if (futureHashMap.containsKey(obDevice.getDevFile())) {
                if (!futureHashMap.get(obDevice.getDevFile()).isDone()) {
                    continue;
                }
            }
            Future<Void> submitFuture = executorService.submit(new EventHubTask(obDevice));
            futureHashMap.put(obDevice.getDevFile(), submitFuture);
        }
    }

    public EventHub(InputManager inputManager){
        this(inputManager,inputManager.getDevices());
    }



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

    public void quit() {
        executorService.shutdownNow();
        rawEventPool.clear();
        Log.d(TAG, "quit.." + executorService);
    }

    class EventHubTask implements Callable<Void>{
        private InputDevice inputDevice;

        public EventHubTask(InputDevice inputDevice) {
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
