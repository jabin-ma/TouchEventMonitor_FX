package com.android.ddmlib.input.android;

import com.android.ddmlib.Log;
import com.android.ddmlib.SetGetble;
import com.android.ddmlib.input.EventData;
import com.android.ddmlib.input.Field;

import java.util.ArrayList;
import java.util.LinkedList;

public class MonitorEventItem implements MonitorEvent {
    private static final String TAG = "MonitorEventItem";
    private static final boolean DEBUG = true;


    int mCurState = 0;


    private static final int STATE_CREATE = 1;
    private static final int STATE_PUB = 2;
    private static final int STATE_ARG = 3;

    private LinkedList<TouchPoint> mTouchEventPath = new LinkedList<TouchPoint>();
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
                if (DEBUG) Log.d(TAG, "onSync-->ARG");
                touchPoint.setTimestamp(rawEvent.getTime().ms);
                Log.d(TAG,"close? "+touchPoint.isClose());
                if(!touchPoint.isClose())
                {
                    touchPoint.close(mTouchEventPath.getLast());
                }
                mTouchEventPath.add(touchPoint);
                Log.d(TAG,"close2? "+touchPoint.isClose());
                touchPoint=null;
                break;
            case STATE_CREATE:
                if (DEBUG) Log.d(TAG, "onSync-->CREATE");
                break;
            case STATE_PUB:
                if (DEBUG) Log.d(TAG, "onSync-->PUB");
                break;
        }
        mCurState=0;
    }

    @Override
    public void onPublish(RawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "onPublish-->" + rawEvent);
        if(mCurState==STATE_CREATE){
        }
        mCurState=STATE_PUB;
    }

    private TouchPoint touchPoint;
    @Override
    public void onArgs(RawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "onArgs-->" + rawEvent);
        mCurState=STATE_ARG;
        if(touchPoint==null)touchPoint=new TouchPoint();
        switch (rawEvent.getHandleType()) {
            case EVENT_ARG_X:
                 touchPoint.setX(Integer.valueOf(rawEvent.getValue(),16));
                break;
            case EVENT_ARG_Y:
                touchPoint.setY(Integer.valueOf(rawEvent.getValue(),16));
                break;
        }
    }
}
