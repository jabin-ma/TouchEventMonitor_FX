package com.android.ddmlib.input;

import java.util.ArrayList;
import java.util.LinkedList;

import com.android.ddmlib.SetGetble;

public class TouchEvent implements SetGetble<Field, String> {
	private boolean close = false;
	private LinkedList<TouchPoint> mTouchEventPath = new LinkedList<TouchPoint>();
	private long closeTime;
	
	TouchEvent(TextEventItem down) {
		super();
	}

	 void moveX(String x) {
		createPoint().setX(Integer.valueOf(x, 16));
	}

	 void moveY(String y) {
		createPoint().setY(Integer.valueOf(y, 16));
	}

	private TouchPoint createPoint() {
		if (mTouchEventPath.isEmpty() || mTouchEventPath.getLast().isClose())
			mTouchEventPath.add(new TouchPoint());
		return mTouchEventPath.getLast();
	}

	 void sync(Time time) {
		if (!close) {
			TouchPoint tp = mTouchEventPath.getLast();
			if (!tp.isClose() && mTouchEventPath.indexOf(tp) != 0) {
				tp.close(mTouchEventPath.get(mTouchEventPath.indexOf(tp) - 1));// 以上一个为基础,关闭point
			}
			tp.setTimestamp(time.ms);
		} else {// up...do someting
			closeTime = time.ms;
		}
	}
	
	
	public String[] toMonkeyCommond() {
		ArrayList<String> result = new ArrayList<String>();
		result.add(String.format("touch down %s", mTouchEventPath.getFirst().toArgs()));
		for (TouchPoint tp : mTouchEventPath) {
			int prev = mTouchEventPath.indexOf(tp);
			if (prev != 0) {
				TouchPoint prevTp = mTouchEventPath.get(prev - 1);
				result.add(String.format("sleep %s", tp.getTimestamp() - prevTp.getTimestamp()));
			}
			result.add(String.format("touch move %s", tp.toArgs()));
		}
		result.add(String.format("sleep %s", closeTime - mTouchEventPath.getLast().getTimestamp()));
		result.add(String.format("touch up %s", mTouchEventPath.getFirst().toArgs()));
		String[] resultarray = new String[result.size()];
		return result.toArray(resultarray);
	}

	 void close() {
		close = true;
	}

	public boolean isClose() {
		return close;
	}
	

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		TouchPoint first = mTouchEventPath.getFirst();
		TouchPoint last = mTouchEventPath.getLast();
		sb.append("从(");
		sb.append(first.getX());
		sb.append(",");
		sb.append(first.getY());
		sb.append(")");
		sb.append(" 到 (");
		sb.append(last.getX());
		sb.append(",");
		sb.append(last.getY());
		sb.append(")");
		return sb.toString();
	}
	
	@Override
	public String get(Field fie) {
		switch (fie) {
		case START_X:
			return String.valueOf(mTouchEventPath.getFirst().getX());
		case START_Y:
			return String.valueOf(mTouchEventPath.getFirst().getY());
		case END_X:
			return String.valueOf(mTouchEventPath.getLast().getX());
		case END_Y:
			return String.valueOf(mTouchEventPath.getLast().getX());
		case Duration:
			return  String.valueOf(getDuration());
		default:
			return "NULL";
		}
	}
	
	
	public long getCloseTime(){
		return closeTime;
	}
	
	
	public long getOpenTime(){
		return getLast().getTimestamp();
	}
	
	public long getDuration() {
		return closeTime - mTouchEventPath.getFirst().getTimestamp();
	}

	@Override
	public void set(Field key, String arg) {

	}
	
	public TouchPoint getFirst(){
		return mTouchEventPath.getFirst();
	}
	
	public TouchPoint getLast(){
		return mTouchEventPath.getLast();
	}
}
