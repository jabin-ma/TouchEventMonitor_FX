package com.android.ddmlib.input.android;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * Created by majipeng on 2017/6/21.
 */
public interface MonitorEvent {
    void onCreate(RawEvent rawEvent);

    void onSync(RawEvent rawEvent);

    void onPublish(RawEvent rawEvent);

    void onArgs(RawEvent rawEvent);

    void setDispatched();

    int dispatchCount();

    SimpleBooleanProperty closedProperty();

    SimpleStringProperty eventTypeProperty();

    SimpleStringProperty eventDescProperty();

    SimpleStringProperty eventDurProperty();

    SimpleStringProperty inputDeviceProperty();
}
