package com.android.ddmlib.input;

public class Time {
	long ms;

	public Time(String str) {
		str = str.trim().replace(" +", "");
		if (AndroidEventItem.ignore(str) || !str.contains("."))
			return;
		String[] sms = str.split("\\.");
		ms = Long.parseLong(sms[0] + sms[1].substring(0, 3));
	}

	public long math(Time other) {
		return 0;
	}

	@Override
	public String toString() {
		return ms + "ms";
	}
}
