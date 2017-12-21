package com.android.ddmlib.input;

import com.android.ddmlib.controller.IRemoteController;
import com.android.ddmlib.utils.Log;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * 触摸事件
 *
 */
public class TouchEvent extends AbsMonitorEvent {
    private static final String TAG = "MonitorEventItem";
    private static final boolean DEBUG = false;
    private LinkedList<TouchPoint> mTouchEventPath = new LinkedList<TouchPoint>();
    private TouchPoint touchPoint;
    private TouchRegion region = new TouchRegion();

    @Override
    public void onCreate(IRawEvent rawEvent) {
        super.onCreate(rawEvent);
        if (DEBUG) Log.d(TAG, "Create-->" + rawEvent);
        addFlags(FLAG_WAIT_SYNC_CREATE);
    }

    @Override
    public void onSync(IRawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "onSync-->" + rawEvent);
        if(hasFlags(FLAG_WAIT_SYNC_ARG))
        {
            if (DEBUG) Log.d(TAG, "onSync-->SetArg");
            touchPoint.setTimestamp(rawEvent.getWhen().ms);
            if(!hasFlags(FLAG_WAIT_SYNC_ARG_X | FLAG_WAIT_SYNC_ARG_Y)){// X & Y is not set
                //we need fix it!
                if(hasFlags(FLAG_WAIT_SYNC_CREATE)){//first, we cannot fix it
                    addFlags(FLAG_NEED_FIX);
                }else{//we can fix it
                    if(!hasFlags(FLAG_WAIT_SYNC_ARG_X)){
                        if(DEBUG)Log.d(TAG,"fix X");
                        touchPoint.setX(mTouchEventPath.getLast().getX());}
                    else{
                        if(DEBUG)Log.d(TAG,"fix Y");
                        touchPoint.setY(mTouchEventPath.getLast().getY());
                    }
                }
            }else{
                //its ok
            }
            region.update(touchPoint);
            mTouchEventPath.add(touchPoint);
            touchPoint = null;
            removeFlags(FLAG_WAIT_SYNC_CREATE | FLAG_WAIT_SYNC_ARG_Y | FLAG_WAIT_SYNC_ARG_X | FLAG_WAIT_SYNC_ARG);
        }
        if(hasFlags(FLAG_WAIT_SYNC_PUBLISH))
        {
            removeFlags(FLAG_WAIT_SYNC_PUBLISH);
            if (DEBUG) Log.d(TAG, "onSync-->Publish");
            publishSync();
        }
    }

    private void publishSync() {
        publishProperty().setValue(true);
        eventDescProperty().setValue(mTouchEventPath.getFirst() + "->" + mTouchEventPath.getLast());
        analyzeEventType();
    }

    @Override
    public void onPublish(IRawEvent rawEvent) {
        super.onPublish(rawEvent);
        if (DEBUG) Log.d(TAG, "onPublish-->" + rawEvent);
        addFlags(FLAG_WAIT_SYNC_PUBLISH);
    }


    @Override
    public void onArgs(IRawEvent rawEvent) {
        if (DEBUG) Log.d(TAG, "onArgs-->" + rawEvent);
        if (touchPoint == null) touchPoint = new TouchPoint();
        addFlags(FLAG_WAIT_SYNC_ARG);
        switch (rawEvent.getHandleType()) {
            case EVENT_ARG_X:
                addFlags(FLAG_WAIT_SYNC_ARG_X);
                touchPoint.setX(Integer.valueOf(rawEvent.getValue(), 16));
                break;
            case EVENT_ARG_Y:
                addFlags(FLAG_WAIT_SYNC_ARG_Y);
                touchPoint.setY(Integer.valueOf(rawEvent.getValue(), 16));
                break;
        }
    }


    public void processController(IRemoteController controller) {
        ListIterator<TouchPoint> listIterator = mTouchEventPath.listIterator(0);
        TouchPoint tp = listIterator.next();
        controller.touchDown(tp.getX(), tp.getY());
        statusProperty().setValue(String.format("回放中...."));
        while (listIterator.hasNext()) {
            tp = listIterator.next();
            controller.touchMove(tp.getX(), tp.getY());
            if (listIterator.hasNext()) {
                TouchPoint next = listIterator.next();
                controller.sleep(next.getTimestamp() - tp.getTimestamp());
                listIterator.previous();
            }
        }
        tp = listIterator.hasPrevious() ? listIterator.previous() : tp;
        controller.touchUp(tp.getX(), tp.getY());
        statusProperty().setValue(String.format("回放完成"));
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

    @Override
    public boolean fixEvent(MonitorEvent monitorEvent) {
        Log.d(TAG,monitorEvent.eventDescProperty().getValue()+" for fix :"+this.eventDescProperty().getValue());
        if(!(monitorEvent instanceof TouchEvent))return false;//fix fail
        for (TouchPoint point : mTouchEventPath) {
            point.fixPoint(((TouchEvent) monitorEvent).mTouchEventPath.getLast());
        }
        publishSync();
        return true;
    }
}
