package com.android.ddmlib.input;

public interface IEvent {
	
	public Time getTime();
	
	
	public String getType();


	public void setType(String type);


	public String getCode();


	public void setCode(String code);


	public String getValue();


	public void setValue(String value);
}
