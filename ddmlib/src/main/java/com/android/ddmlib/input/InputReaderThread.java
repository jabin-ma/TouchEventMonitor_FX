package com.android.ddmlib.input;

import com.android.ddmlib.Log;

/**
 * Created by majipeng on 2017/6/23.
 */
public class InputReaderThread extends Thread {
    private InputManager mContext;

    private boolean run = true;

    private static final String TAG = "InputReaderThread";

    public InputReaderThread(InputManager context) {
        setName("inputReader-Thread");
        this.mContext = context;
    }


    public void onFinish() {
        run = false;
    }

    @Override
    public void run() {
        while (run) {
            MonitorEvent monitorEvent = mContext.inputReader.readBySync();
            if (monitorEvent != null) {
                mContext.getInputDispatcher().dispatchEvent(monitorEvent);
            }
        }
        Log.d(TAG, "finish..");
    }
}
