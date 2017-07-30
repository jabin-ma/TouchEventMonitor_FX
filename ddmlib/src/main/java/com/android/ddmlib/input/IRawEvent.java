package com.android.ddmlib.input;

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
