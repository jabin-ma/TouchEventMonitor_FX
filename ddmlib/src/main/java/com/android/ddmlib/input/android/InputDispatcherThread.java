package com.android.ddmlib.input.android;

import com.android.ddmlib.Log;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputDispatcherThread extends Thread {
    InputManager mContext;

    public InputDispatcherThread(InputManager mContext) {
        setName("InputDispatcher-Thread");
        this.mContext = mContext;
    }

    @Override
    public void run() {
        while (true) {
            try {
                MonitorEvent dispatch = mContext.getInputDispatcher().getWaitingForDispatchEvent();
                Log.d("dispatch", dispatch.getInputDevice() + "---->" + dispatch);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
