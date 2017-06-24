package com.android.ddmlib.input.android;

import com.android.ddmlib.Log;

import java.util.LinkedList;

public class MonitorEventItem implements MonitorEvent {
    private static final String TAG = "MonitorEventItem";
    private static final boolean DEBUG = false;
    private int mCurState = 0;
    private static final int STATE_CREATE = 1;
    private static final int STATE_PUB = 2;
    private static final int STATE_ARG = 3;
    private LinkedList<TouchPoint> mTouchEventPath = new LinkedList<TouchPoint>();
    private boolean closed = false;
    private TouchPoint touchPoint;
    private String inputDevice;


    public void setInputDevice(String inputDevice) {
        this.inputDevice = inputDevice;
    }

    public String getInputDevice() {
        return inputDevice;
    }

    @Override
    public void onCreate(RawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "Create-->" + rawEvent);
        mCurState = STATE_CREATE;

    }

    @Override
    public void onSync(RawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "onSync-->" + rawEvent);
        switch (mCurState) {
            case STATE_ARG:
                if (DEBUG) Log.d(TAG, "onSync-->setarg");
                touchPoint.setTimestamp(rawEvent.getTime().ms);
                if (!touchPoint.isClose() && !mTouchEventPath.isEmpty()) {
                    touchPoint.close(mTouchEventPath.getLast());
                }
                mTouchEventPath.add(touchPoint);
                touchPoint = null;
                break;
            case STATE_CREATE:
                if (DEBUG) Log.d(TAG, "onSync-->create");
                break;
            case STATE_PUB:
                if (DEBUG) Log.d(TAG, "onSync-->close");
                closed = true;
                break;
        }
        mCurState = 0;
    }

    @Override
    public void onPublish(RawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "onPublish-->" + rawEvent);
        if (mCurState == STATE_CREATE) {
        }
        mCurState = STATE_PUB;
    }


    @Override
    public void onArgs(RawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "onArgs-->" + rawEvent);
        mCurState = STATE_ARG;
        if (touchPoint == null) touchPoint = new TouchPoint();
        switch (rawEvent.getHandleType()) {
            case EVENT_ARG_X:
                touchPoint.setX(Integer.valueOf(rawEvent.getValue(), 16));
                break;
            case EVENT_ARG_Y:
                touchPoint.setY(Integer.valueOf(rawEvent.getValue(), 16));
                break;
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
