package com.android.ddmlib.input;

public interface IRawEvent {

    public When getWhen();


    public String getType();


    public void setType(String type);


    public String getCode();


    public void setCode(String code);


    public String getValue();


    public void setValue(String value);
}
