package com.android.ddmlib.input.android;

import com.android.ddmlib.Log;
import javafx.beans.property.SimpleBooleanProperty;
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
    private final SimpleStringProperty eventDur = new SimpleStringProperty();
    private final SimpleStringProperty inputDevice = new SimpleStringProperty();
    private final SimpleBooleanProperty closed = new SimpleBooleanProperty();
    private RawEvent begin, end;

    @Override
    public void onCreate(RawEvent rawEvent) {
        begin = rawEvent;
        closed.addListener(this);
        Log.d("absMonitor", "create:" + begin.getTime().ms);
    }


    @Override
    public void onPublish(RawEvent rawEvent) {
        end = rawEvent;
        Log.d("absMonitor", "onPublish:" + end.getTime().ms);
    }

    @Override
    public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        //closed
        Log.d("absMonitor", "close change:" + (end.getTime().ms - begin.getTime().ms));
        closed.removeListener(this);
        eventDur.setValue((end.getTime().ms - begin.getTime().ms) + "ms");
    }

    public String getEventType() {
        return eventType.get();
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
    public SimpleStringProperty eventDurProperty() {
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
}
