package com.android.ddmlib.input;

/**
 * 原始数据event,即getevent返回的数据
 */
public interface IRawEvent {
    /**
     * 获取当前事件的Owner
     *
     * @return 设备
     */
    String getOwner();

    /**
     * event时间
     *
     * @return 事件发生时间
     */
    When getWhen();

    /**
     * 事件类型
     *
     * @return 事件类型
     */
    String getType();


    void setType(String type);


    String getCode();


    void setCode(String code);


    String getValue();


    void setValue(String value);


    KnownEventList.HandleType getHandleType();

    void setHandleType(KnownEventList.HandleType type);

    String getEventClass();

    void setEventClass(String ec);
}
