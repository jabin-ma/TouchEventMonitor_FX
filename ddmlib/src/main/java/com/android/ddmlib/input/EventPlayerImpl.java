package com.android.ddmlib.input;

import com.android.ddmlib.controller.IRemoteController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class EventPlayerImpl implements EventPlayer, Runnable {
    private static final String TAG = "EventPlayerImpl";
    private IRemoteController mIrc;
    LinkedBlockingQueue<Message> blockingQueue = new LinkedBlockingQueue();
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    private PlayerListener listener;

    public EventPlayerImpl(IRemoteController mIrc,PlayerListener listener) {
        this.mIrc = mIrc;
        this.listener=listener;
    }

    @Override
    public void start() {
        blockingQueue.offer(new Message(2, null));
        executorService.execute(this);
    }

    @Override
    public void pause() {

    }

    @Override
    public void stop() {

    }

    public EventPlayer addData(MonitorEvent monitorEvent) {
        blockingQueue.offer(new Message(0, monitorEvent));
        return this;
    }

    public EventPlayer sleep(long duration) {
        blockingQueue.offer(new Message(1, duration));
        return this;
    }

    @Override
    public void run() {
        this.listener.onStart();
        while (true) {
            try {
                Message msg = blockingQueue.take();
                if (handleMessage(msg)) break;
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
        this.listener.onStop();
    }


    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case 0:
                MonitorEvent item = (MonitorEvent) msg.obj;
                item.processController(mIrc);
                break;
            case 1:
                mIrc.sleep((Long) msg.obj);
                break;
            case 2:
                return true;
            case 3:
                break;
            case 4:
                break;
            default:
                break;
        }
        return false;
    }


    class Message {
        int what;
        Object obj;

        public Message(int what, Object obj) {
            this.what = what;
            this.obj = obj;
        }
    }
}
