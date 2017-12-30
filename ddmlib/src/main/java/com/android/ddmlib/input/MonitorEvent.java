package com.android.ddmlib.input;

import com.android.ddmlib.controller.IRemoteController;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * Created by majipeng on 2017/6/21.
 * <p>
 * 解析后的事件,与Raw相对应
 */
public interface MonitorEvent {

    //等待CREATE的同步事件
    static final int FLAG_WAIT_SYNC_CREATE = 1;
    //等待Publish
    static final int FLAG_WAIT_SYNC_PUBLISH = 1 << 1;

    //数据同步
    static final int FLAG_WAIT_SYNC_ARG = 1 << 4;
    static final int FLAG_WAIT_SYNC_ARG_X = 1 << 2;
    static final int FLAG_WAIT_SYNC_ARG_Y = 1 << 3;
    /**
     * 需要基于上个Event进行修复
     */
    static final int FLAG_NEED_FIX = 1 << 5;

    /**
     * 对应事件类型 EVENT_CREATE
     *
     * @param rawEvent
     */
    void onCreate(IRawEvent rawEvent);

    /**
     * 一次事件同步
     *
     * @param rawEvent
     */
    void onSync(IRawEvent rawEvent);

    /**
     * EVENT_PUBLISH,发布该事件
     *
     * @param rawEvent
     */
    void onPublish(IRawEvent rawEvent);

    /**
     * 事件参数
     *
     * @param rawEvent
     */
    void onArgs(IRawEvent rawEvent);

    SimpleBooleanProperty publishProperty();

    SimpleStringProperty eventTypeProperty();

    SimpleStringProperty eventDescProperty();

    SimpleLongProperty eventDurProperty();

    SimpleStringProperty inputDeviceProperty();

    SimpleStringProperty statusProperty();
//    TouchEvent.Type getEventType();

    long beginTime();

    long endTime();

    void processController(IRemoteController controller);

    /**
     * 是否包含flag
     *
     * @param flag
     * @return
     */
    boolean hasFlags(int flag);

    /**
     * 修复该event
     *
     * @param monitorEvent 基准数据
     * @return 修复是否成功
     */
    boolean fixEvent(MonitorEvent monitorEvent);
}
