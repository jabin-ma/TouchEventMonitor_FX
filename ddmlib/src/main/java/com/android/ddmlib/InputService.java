package com.android.ddmlib;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class InputService implements IShellOutputReceiver {

	IDevice dev;
	
	Transport transport;
	
	
	public InputService(IDevice dev) {
		super();
		this.dev = dev;
		transport=new Transport(dev, this);
		try {
			transport.enter(500, TimeUnit.MILLISECONDS);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	public void getDevice(){
	 transport.send("getevent -p");	
	}


	@Override
	public void addOutput(byte[] data, int offset, int length) {
		System.out.println("======"+new String(data,offset,length).replaceAll("\n +", ""));
	}


	@Override
	public void flush() {
		
	}


	@Override
	public boolean isCancelled() {
		return false;
	}
}
