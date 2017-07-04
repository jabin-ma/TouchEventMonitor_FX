package com.android.ddmlib.input;

import com.android.ddmlib.Log;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Created by majipeng on 2017/6/30.
 */
public abstract class AbsMonitorEvent implements MonitorEvent, ChangeListener<Boolean> {
    private int dispatchCount = 0;
    private final SimpleStringProperty eventType = new SimpleStringProperty();
    private final SimpleStringProperty eventDesc = new SimpleStringProperty();
    private final SimpleLongProperty eventDur = new SimpleLongProperty();
    private final SimpleStringProperty inputDevice = new SimpleStringProperty();
    private final SimpleBooleanProperty closed = new SimpleBooleanProperty();
    private IRawEvent begin, end;

    @Override
    public void onCreate(PlainTextRawEvent rawEvent) {
        begin = rawEvent;
        closed.addListener(this);
        Log.d("absMonitor", "create:" + begin.getWhen().ms);
    }

    @Override
    public void onPublish(PlainTextRawEvent rawEvent) {
        end = rawEvent;
        Log.d("absMonitor", "onPublish:" + end.getWhen().ms);
    }

    @Override
    public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        //closed
        Log.d("absMonitor", "close change:" + (end.getWhen().ms - begin.getWhen().ms));
        closed.removeListener(this);
        eventDur.setValue((end.getWhen().ms - begin.getWhen().ms));
    }

    public TouchEvent.Type getEventType() {
        return TouchEvent.Type.valueOf(eventType.get());
    }

    @Override
    public SimpleStringProperty eventTypeProperty() {
        return eventType;
    }

    @Override
    public SimpleStringProperty eventDescProperty() {
        return eventDesc;
    }

    @Override
    public SimpleLongProperty eventDurProperty() {
        return eventDur;
    }

    public SimpleBooleanProperty closedProperty() {
        return closed;
    }

    @Override
    public SimpleStringProperty inputDeviceProperty() {
        return inputDevice;
    }

    @Override
    public void setDispatched() {
        dispatchCount++;
    }

    @Override
    public int dispatchCount() {
        return dispatchCount;
    }


    @Override
    public long beginTime() {
        return begin.getWhen().ms;
    }

    @Override
    public long endTime() {
        return end.getWhen().ms;
    }
}
