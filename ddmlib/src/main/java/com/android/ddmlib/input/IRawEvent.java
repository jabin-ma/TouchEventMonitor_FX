package com.android.ddmlib.input;

/**
 * 原始数据event,即getevent返回的数据
 */
public interface IRawEvent {

     When getWhen();


     String getType();


     void setType(String type);


     String getCode();


     void setCode(String code);


     String getValue();


     void setValue(String value);


     KnownEventList.HandleType getHandleType();
}
