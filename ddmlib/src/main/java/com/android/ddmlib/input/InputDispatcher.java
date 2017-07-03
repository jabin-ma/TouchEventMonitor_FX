package com.android.ddmlib.input;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputDispatcher {
    private BlockingQueue<MonitorEvent> waitingForDispatch = new LinkedBlockingQueue<>(2);

    public InputDispatcher(InputManager inputManager) {
    }

    void dispatchEvent(MonitorEvent event) {
        waitingForDispatch.add(event);
    }

    MonitorEvent getWaitingForDispatchEvent() throws InterruptedException {
        return waitingForDispatch.take();
    }
}
