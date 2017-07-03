package com.android.ddmlib.input;

/**
 * Created by majipeng on 2017/6/23.
 */
public class InputReaderThread extends Thread {
    private InputManager mContext;

    private boolean read = true;

    public InputReaderThread(InputManager context) {
        setName("inputReader-Thread");
        this.mContext = context;
    }

    @Override
    public void run() {
        while (read) {
            MonitorEvent monitorEvent = mContext.inputReader.readBySync();
            if (monitorEvent != null) {
                mContext.getInputDispatcher().dispatchEvent(monitorEvent);
            }
        }
    }
}
