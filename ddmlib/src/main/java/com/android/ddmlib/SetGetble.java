package com.android.ddmlib;

public interface SetGetble<KEY,RESULT> {

	public void set(KEY key,String arg);
	
	public RESULT get(KEY key);
	
}
