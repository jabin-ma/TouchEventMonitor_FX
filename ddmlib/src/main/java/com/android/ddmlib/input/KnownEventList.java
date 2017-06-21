package com.android.ddmlib.input;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.android.ddmlib.Log;
import com.android.ddmlib.input.android.RawEvent;

public class KnownEventList {

	public List<RawEvent> needwhatch = new ArrayList<RawEvent>();

	public KnownEventList(String file) {
		InputStream in = null;
		try {
			in = new FileInputStream(file);
			read(in);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {

			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void read(InputStream in) throws IOException {
		InputStreamReader ins = null;
		BufferedReader read = null;
		ins = new InputStreamReader(in);
		read = new BufferedReader(ins);
		String line;
		while ((line = read.readLine()) != null) {
			if (line.startsWith("#"))
				continue;
			needwhatch.add(new RawEvent(line,null));
		}
		read.close();
		ins.close();
	}

	KnownEventList() {
		try {
			read(getClass().getResourceAsStream("KnownEventList.default"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean need(RawEvent e) {
		return false;
	}

	public HandleType queryHandleType(RawEvent in) {
		for (RawEvent def : needwhatch) {
			if (def.equals(in)) {
				return def.getHandleType();
			}
		}
		return HandleType.UNKNOWN;
	}

	public enum HandleType {
		TOUCH_DOWN, TOUCH_X, TOUCH_Y, TOUCH_UP, TOUCH_SYNC, UNKNOWN;

		public static HandleType get(int type) {
			switch (type) {
			case 1:
				return TOUCH_DOWN;
			case 2:
				return TOUCH_X;
			case 3:
				return TOUCH_Y;
			case 4:
				return TOUCH_UP;
			case 5:
				return HandleType.TOUCH_SYNC;
			default:
				Log.d("HandleType", "UnKnown HandleType:" + type);
				return UNKNOWN;
			}
		}
	}
}
