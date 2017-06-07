package com.android.ddmlib.input;

import com.android.ddmlib.input.NeedWhatchKey.HandleType;

public class AndroidEventItem implements IEvent {

	private Time time = null;
	private String type = null, code = null, value = null;
	private HandleType handleType;

	public AndroidEventItem(String str) {
		String[] args = str.replaceAll("[\\[\\]]", "").replaceAll(" +", " ").trim().split(" ");
		for (int i = 0; i < args.length; i++) {
			set(i, args[i]);
		}
	}

	public void set(int index, String v) {
		switch (index) {
		case 0:
			setTime(new Time(v));
			break;
		case 1:
			setType(v);
			break;
		case 2:
			setCode(v);
			break;
		case 3:
			value = v;
			setValue(value);
			break;
		case 4:
			setHandleType(HandleType.get(Integer.valueOf(v)));
			break;
		default:
			break;
		}
	}

	@Override
	public String toString() {
		return "========EventItem========\ntime=" + time + "\ntype=" + type + "\ncode=" + code + "\nvalue=" + value;
	}

	static boolean ignore(String str) {
		return "ignore".equals(str);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AndroidEventItem other = (AndroidEventItem) obj;
		// // time
		// if (!ignore(time.toString()) && !ignore(other.time.toString())) {
		// if (!time.equals(other.time))
		// return false;
		// }
		// type
		if (!ignore(type) && !ignore(other.type)) {
			if (!type.equals(other.type))
				return false;
		}
		// name
		if (!ignore(code) && !ignore(other.code)) {
			if (!code.equals(other.code))
				return false;
		}
		// value
		if (!ignore(value) && !ignore(other.value)) {
			if (!value.equals(other.value))
				return false;
		}
		return true;
	}

	@Override
	public Time getTime() {
		return time;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public void setCode(String code) {
		this.code = code;
	}

	@Override
	public String getValue() {
		return value;
	}

	@Override
	public void setValue(String value) {
		this.value = value;
	}

	public HandleType getHandleType() {
		return handleType;
	}

	public void setHandleType(HandleType handleType) {
		this.handleType = handleType;
	}

	public void setTime(Time time) {
		this.time = time;
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

}
