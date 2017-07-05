package com.android.ddmlib.input;

import com.android.ddmlib.Log;
import com.android.ddmlib.controller.IRemoteController;

import java.util.LinkedList;
import java.util.ListIterator;

public class TouchEvent extends AbsMonitorEvent {
    private static final String TAG = "MonitorEventItem";
    private static final boolean DEBUG = false;
    private int mCurState = 0;
    private static final int STATE_CREATE = 1;
    private static final int STATE_PUB = 2;
    private static final int STATE_ARG = 3;
    private LinkedList<TouchPoint> mTouchEventPath = new LinkedList<TouchPoint>();
    private TouchPoint touchPoint;
    private TouchRegion region = new TouchRegion();

    @Override
    public void onCreate(IRawEvent rawEvent) {
        super.onCreate(rawEvent);
        if (DEBUG) Log.d(TAG, "Create-->" + rawEvent);
        mCurState = STATE_CREATE;
    }

    @Override
    public void onSync(IRawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "onSync-->" + rawEvent);
        switch (mCurState) {
            case STATE_ARG:
                if (DEBUG) Log.d(TAG, "onSync-->setarg");
                touchPoint.setTimestamp(rawEvent.getWhen().ms);
                if (!touchPoint.isClose() && !mTouchEventPath.isEmpty()) {
                    touchPoint.close(mTouchEventPath.getLast());
                }
                region.update(touchPoint);
                mTouchEventPath.add(touchPoint);
                touchPoint = null;
                break;
            case STATE_CREATE:
                if (DEBUG) Log.d(TAG, "onSync-->create");
                break;
            case STATE_PUB:
                if (DEBUG) Log.d(TAG, "onSync-->close");
                closedProperty().setValue(true);
                eventDescProperty().setValue(mTouchEventPath.getFirst() + "->" + mTouchEventPath.getLast());
                analyzeEventType();
                break;
        }
        mCurState = 0;
    }

    @Override
    public void onPublish(IRawEvent rawEvent) {
        super.onPublish(rawEvent);
        if (DEBUG) Log.d(TAG, "onPublish-->" + rawEvent);
        if (mCurState == STATE_CREATE) {
        }
        mCurState = STATE_PUB;
    }


    @Override
    public void onArgs(IRawEvent rawEvent) {
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


    public void processController(IRemoteController controller) {
        ListIterator<TouchPoint> listIterator = mTouchEventPath.listIterator(0);
        TouchPoint tp = null;
        tp = listIterator.next();
        controller.touchDown(tp.getX(), tp.getY());
        while (listIterator.hasNext()) {
            tp = listIterator.next();
            controller.sleep(tp.getTimestamp() - listIterator.previous().getTimestamp());
            listIterator.next();
            controller.touchMove(tp.getX(), tp.getY());
        }
        tp = listIterator.hasPrevious() ? listIterator.previous() : tp;
        controller.touchUp(tp.getX(), tp.getY());
    }

    public void analyzeEventType() {
        if (region.offsetX() > 100 || region.offsetY() > 100) {
            eventTypeProperty().set(Type.Move.name());
        } else {
            eventTypeProperty().set(Type.Click.name());
            if (eventDurProperty().get() > 500) {
                eventTypeProperty().set(Type.LongClick.name());
            }
        }
    }

    @Override
    public String toString() {
        return "点击事件";
    }


    enum Type {
        Click("单击事件"), Move("移动事件"), LongClick("长按事件");
        String desc;

        Type(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return desc;
        }
    }
}
