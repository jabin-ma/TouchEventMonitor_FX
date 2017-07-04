package com.android.ddmlib.input;

import com.android.ddmlib.controller.IRemoteController;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.ListIterator;

/**
 * Created by majipeng on 2017/6/21.
 */
public interface MonitorEvent {

    void onCreate(PlainTextRawEvent rawEvent);

    void onSync(PlainTextRawEvent rawEvent);

    void onPublish(PlainTextRawEvent rawEvent);

    void onArgs(PlainTextRawEvent rawEvent);

    void setDispatched();

    int dispatchCount();


    SimpleBooleanProperty closedProperty();

    SimpleStringProperty eventTypeProperty();

    SimpleStringProperty eventDescProperty();

    SimpleLongProperty eventDurProperty();

    SimpleStringProperty inputDeviceProperty();

    TouchEvent.Type getEventType();

    long beginTime();

    long endTime();

     void processController(IRemoteController controller);
}
