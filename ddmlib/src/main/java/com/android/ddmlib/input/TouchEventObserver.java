package com.android.ddmlib.input;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.OneLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.input.NeedWhatchKey.HandleType;

public class TouchEventObserver extends OneLineReceiver implements Runnable {

	private static final boolean DEBUG = true;

	private static final String TAG = "GetEventReceiver";

	private NeedWhatchKey whatchKeys = new NeedWhatchKey();
	private onTouchEventListener mTouchEventListener;

	public TouchEventObserver(IDevice device, onTouchEventListener mTouchEventListener) {
		super();
		this.mTouchEventListener = mTouchEventListener;
		this.device = device;
	}

	private IDevice device;

	private boolean running;

	private ExecutorService executorService = Executors.newSingleThreadExecutor();

	@Override
	public void processNewLines(String line) {
		TextEventItem event = new TextEventItem(line);
		HandleType type = whatchKeys.queryHandleType(event);
		if (HandleType.UNKNOWN != type) {
			event.setHandleType(type);
			if (DEBUG)
				Log.d(TAG, "process line:" + line);
			handleEventItem(event, type);
		} else { //

		}
	}

	private TouchEvent tempTouchEvent;

	private void handleEventItem(TextEventItem event, HandleType type) {
		if (type != HandleType.TOUCH_DOWN && tempTouchEvent == null)
			return;
		switch (type) {
		case TOUCH_DOWN:
			tempTouchEvent = new TouchEvent(event);
			break;
		case TOUCH_X:
			tempTouchEvent.moveX(event.getValue());
			break;
		case TOUCH_Y:
			tempTouchEvent.moveY(event.getValue());
			break;
		case TOUCH_SYNC:
			tempTouchEvent.sync(event.getTime());
			if (tempTouchEvent.isClose()) {// 事件结束,set null
				if (mTouchEventListener != null)
					mTouchEventListener.onTouchEvent(tempTouchEvent);
				tempTouchEvent = null;
			}
			break;
		case TOUCH_UP:
			tempTouchEvent.close();
			break;
		default:
			break;
		}
	}

	public void stop() {
		running = false;
	}

	public boolean isClose() {
		return executorService == null || executorService.isShutdown();
	}

	public interface onTouchEventListener {

		public void onMonitorStarted();

		public void onMonitorStoped();

		public void onTouchEvent(TouchEvent event);
	}

	@Override
	public void run() {
		if (device.isOnline()) {
			running = true;
			mTouchEventListener.onMonitorStarted();
			try {
				device.executeShellCommand("getevent -tl  /dev/input/event3", this, -1, null);
			} catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException
					| IOException e) {
				e.printStackTrace();
			}
			Log.d(TAG, "getevent -tl finish");
		} else {
			Log.d(TAG, "getevent fail DEVICE-NOT-ONLINE");
		}
		mTouchEventListener.onMonitorStoped();
		running = false;
	}

	public boolean monitor() {
		if (running)
			return false;
		if (device != null) {
			executorService.execute(this);
		} else {
			return false;
		}
		return true;

	}

	@Override
	public boolean isCancelled() {
		return !running;
	}
}
