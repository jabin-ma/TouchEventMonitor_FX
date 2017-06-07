package com.android.ddmlib.input;

public class TouchPoint {
	
	private int x = Integer.MIN_VALUE, y = Integer.MIN_VALUE;

	private long timestamp = Integer.MIN_VALUE;

	private boolean close;

	boolean close(TouchPoint previous) {
		setX(previous.x);
		setY(previous.y);
		return isClose();
	}

	private void close() {
		if (isClose(x) && isClose(y) && isClose(timestamp))
			close = true;
	}

	public boolean isClose() {
		return close;
	}

	public int getX() {
		return x;
	}

	 void setX(int x) {
		if (isClose(this.x))
			return;
		this.x = x;
		close();
	}

	public int getY() {
		return y;
	}

	 void setY(int y) {
		if (isClose(this.y))return;
		this.y = y;
		close();
	}

	 boolean isClose(long i) {
		return i != Integer.MIN_VALUE;
	}

	@Override
	public String toString() {
		return x + " " + y;
	}

	
	public long getTimestamp() {
		return timestamp;
	}

	 void setTimestamp(long timestamp) {
		if (isClose(this.timestamp))return;
		this.timestamp = timestamp;
		close();
	}

	public String toArgs() {
		return x + " " + y;
	}
}
