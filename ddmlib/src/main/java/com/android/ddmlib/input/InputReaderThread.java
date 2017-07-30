package com.android.ddmlib.input;

import com.android.ddmlib.Log;

/**
 * Created by majipeng on 2017/6/23.
 */
public class InputReaderThread extends Thread {
    private InputManager mContext;
    private static final String TAG = "InputReaderThread";

    public InputReaderThread(InputManager context) {
        setName("inputReader-Thread");
        this.mContext = context;
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            MonitorEvent monitorEvent = null;
            try {
                monitorEvent = mContext.inputReader.readBySync();
                if (monitorEvent != null) {
                    mContext.getInputDispatcher().dispatchEvent(monitorEvent);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        Log.d(TAG, "finish..");
    }
}
