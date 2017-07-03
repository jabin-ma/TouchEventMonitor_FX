package com.android.ddmlib.input;

import com.android.ddmlib.Log;

import java.util.ArrayList;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputDispatcherThread extends Thread {
    private InputManager mContext;

    private ArrayList<OnTouchEventListener> listeners = new ArrayList<>();

    public InputDispatcherThread(InputManager mContext) {
        setName("InputDispatcher-Thread");
        this.mContext = mContext;
    }


    boolean addOnTouchEventListener(OnTouchEventListener listener) {
        return listeners.add(listener);
    }


    boolean unregisterTouchEventListener(OnTouchEventListener listener) {
        return listeners.remove(listener);
    }


    @Override
    public void run() {
        while (true) {
            try {
                MonitorEvent event = mContext.getInputDispatcher().getWaitingForDispatchEvent();
                Log.d("dispatch", event.inputDeviceProperty() + "---->" + event);
                event.setDispatched();
                for (OnTouchEventListener listener : listeners) {
                    listener.onTouchEvent(event);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


}
