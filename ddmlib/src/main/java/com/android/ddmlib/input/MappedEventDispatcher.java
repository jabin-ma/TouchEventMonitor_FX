package com.android.ddmlib.input;

import com.android.ddmlib.utils.Log;

import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * Created by majipeng on 2017/6/19.
 * 将映射完成的本地事件进行分发
 */
public class MappedEventDispatcher implements Callable<Void> {
    private final EventHubReader eventPool;
    private ArrayList<OnTouchEventListener> listeners = new ArrayList<>();

    private static final String TAG = "MappedEventDispatcher";

    public MappedEventDispatcher(EventHubReader eventPool) {
        this.eventPool = eventPool;
    }


    boolean addOnTouchEventListener(OnTouchEventListener listener) {
        return listeners.add(listener);
    }


    boolean unregisterTouchEventListener(OnTouchEventListener listener) {
        return listeners.remove(listener);
    }


    @Override
    public Void call() {
        while (!Thread.interrupted()) {
            try {
                MonitorEvent event = eventPool.takeMappedEvent();
                if (event == null) continue;
                Log.d("dispatch", event.inputDeviceProperty() + "---->" + event);
                event.setDispatched();
                for (OnTouchEventListener listener : listeners) {
                    listener.onTouchEvent(event);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        Log.d(TAG,"run finish");
        return null;
    }
}

