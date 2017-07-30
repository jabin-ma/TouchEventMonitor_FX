package com.android.ddmlib.input;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by majipeng on 2017/6/19.
 *
 *
 */
public class EventPool {
    private BlockingQueue<MonitorEvent> waitingForDispatch = new LinkedBlockingQueue<>(2);

    public EventPool() {
    }

    void dispatchEvent(MonitorEvent event) {
        waitingForDispatch.add(event);
    }

    MonitorEvent getWaitingForDispatchEvent() throws InterruptedException {
        return waitingForDispatch.take();
    }

}
