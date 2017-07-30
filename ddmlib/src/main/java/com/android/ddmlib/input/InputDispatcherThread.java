package com.android.ddmlib.input;

import com.android.ddmlib.Log;

import java.util.ArrayList;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputDispatcherThread extends Thread {
    private InputManager mContext;

    private ArrayList<OnTouchEventListener> listeners = new ArrayList<>();


    private static final String TAG = "InputDispatcherThread";
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
        while (!isInterrupted()) {
            try {
                MonitorEvent event = mContext.getInputDispatcher().getWaitingForDispatchEvent();
                if(event==null)continue;
                Log.d("dispatch", event.inputDeviceProperty() + "---->" + event);
                event.setDispatched();
                for (OnTouchEventListener listener : listeners) {
                    listener.onTouchEvent(event);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        Log.d(TAG, "finish..");
    }
}
