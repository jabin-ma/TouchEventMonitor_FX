package com.android.ddmlib.input;

import com.android.ddmlib.Log;

/**
 * Created by majipeng on 2017/6/23.
 * <p>
 * 驱动InputReader
 */
public class InputReaderThread extends Thread {
    private static final String TAG = "InputReaderThread";
    private InputReader reader;
    private EventPool inputDispatcher;


    public InputReaderThread(InputReader reader, EventPool inputDispatcher) {
        setName("inputReader-Thread");
        this.reader = reader;
        this.inputDispatcher = inputDispatcher;
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            MonitorEvent monitorEvent = reader.read();
            if (monitorEvent != null) {
                Log.d(TAG, "read:" + monitorEvent);
                inputDispatcher.dispatchEvent(monitorEvent);
            } else {
                Log.d(TAG, "read null .. exit!!");
                break;
            }
        }
        Log.d(TAG, "finish..");
    }
}
