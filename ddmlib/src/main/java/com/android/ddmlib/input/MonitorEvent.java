package com.android.ddmlib.input;

import com.android.ddmlib.controller.IRemoteController;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * Created by majipeng on 2017/6/21.
 *
 * 解析后的事件,与Raw相对应
 */
public interface MonitorEvent {
    /**
     * 对应事件类型 EVENT_CREATE
     * @param rawEvent
     */
    void onCreate(IRawEvent rawEvent);

    /**
     * 一次事件同步
     * @param rawEvent
     */
    void onSync(IRawEvent rawEvent);

    /**
     * EVENT_PUBLISH,发布该事件
     * @param rawEvent
     */
    void onPublish(IRawEvent rawEvent);

    /**
     * 事件参数
     * @param rawEvent
     */
    void onArgs(IRawEvent rawEvent);

    /**
     * 是否被分发过
     */
    void setDispatched();

    /**
     * 分发次数
     * @return
     */
    int dispatchCount();


    SimpleBooleanProperty closedProperty();

    SimpleStringProperty eventTypeProperty();

    SimpleStringProperty eventDescProperty();

    SimpleLongProperty eventDurProperty();

    SimpleStringProperty inputDeviceProperty();

//    TouchEvent.Type getEventType();

    long beginTime();

    long endTime();

    void processController(IRemoteController controller);
}
