package com.android.ddmlib.input;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.Log;
import com.android.ddmlib.OneLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.input.KnownEventList.HandleType;

public class TouchEventObserver extends OneLineReceiver implements Runnable {

    private static final boolean DEBUG = true;

    private static final String TAG = "TouchEventObserver";

    private KnownEventList whatchKeys = new KnownEventList();
    private onTouchEventListener mTouchEventListener;

    public TouchEventObserver(InputDevice device) {
        super();
//		this.mTouchEventListener = mTouchEventListener;
        this.device = device;
    }

    private InputDevice device;

    private boolean running, pause;

    private int state = 0;

    static final int STATE_RUN = 0x00000001;
    static final int STATE_RUNING = 0x00000010;

    static final int STATE_STOPED = 0x00000002;
    static final int STATE_STOPING = 0x00000020;

    static final int STATE_PAUSING = 0x00000003;
    static final int STATE_PAUSED = 0x00000030;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void processNewLines(String line) {
        Log.d(TAG,"process:"+line);
        TextEventItem event = new TextEventItem(line);
        HandleType type = whatchKeys.queryHandleType(event);
        if (HandleType.UNKNOWN != type) {
            event.setHandleType(type);
            if (DEBUG)
                Log.d(TAG, "process line:" + line);
            handleEventItem(event, type);
        } else { //

        }
    }

    private TouchEvent tempTouchEvent;

    private void handleEventItem(TextEventItem event, HandleType type) {
        if (pause) return;
        if (type != HandleType.TOUCH_DOWN && tempTouchEvent == null)
            return;
        switch (type) {
            case TOUCH_DOWN:
                tempTouchEvent = new TouchEvent(event);
                break;
            case TOUCH_X:
                tempTouchEvent.moveX(event.getValue());
                break;
            case TOUCH_Y:
                tempTouchEvent.moveY(event.getValue());
                break;
            case TOUCH_SYNC:
                tempTouchEvent.sync(event.getTime());
                if (tempTouchEvent.isClose()) {// 事件结束,set null
                    if (mTouchEventListener != null)
                        mTouchEventListener.onTouchEvent(tempTouchEvent);
                    tempTouchEvent = null;
                }
                break;
            case TOUCH_UP:
                tempTouchEvent.close();
                break;
            default:
                break;
        }
    }

    public void pause() {
        pause = true;
    }

    public void stop() {
        running = false;
    }

    public boolean isClose() {
        return executorService == null || executorService.isShutdown();
    }

    public interface onTouchEventListener {

        public void onMonitorStarted();

        public void onMonitorStoped();

        public void onTouchEvent(TouchEvent event);
    }

    @Override
    public void run() {
        if (device.getAndroidDevice().isOnline()) {
            running = true;
            mTouchEventListener.onMonitorStarted();
            try {
                device.getAndroidDevice().executeShellCommand(this, -1, Command.GETEVENT_WHATCH_TEXT_EVENT, device.getDevFile());
            } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException
                    | IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "getevent -tl finish");
        } else {
            Log.d(TAG, "getevent fail DEVICE-NOT-ONLINE");
        }
        mTouchEventListener.onMonitorStoped();
        running = false;
    }

    public boolean monitor() {
        if (running)
            return false;
        if (device != null) {
            executorService.execute(this);
        } else {
            return false;
        }
        return true;
    }


    public void setTouchEventListener(onTouchEventListener mTouchEventListener) {
        this.mTouchEventListener = mTouchEventListener;
    }

    @Override
    public boolean isCancelled() {
        return !running;
    }
}
