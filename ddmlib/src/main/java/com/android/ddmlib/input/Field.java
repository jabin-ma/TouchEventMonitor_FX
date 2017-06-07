package com.android.ddmlib.input;
public enum Field {
	START_X("起点X"), START_Y("起点Y"), END_X("终点X"), END_Y("终点Y"),Duration("耗时(ms)"),TYPE("类型");
	private String desc;

	private Field(String desc) {
		this.desc = desc;
	}

	public String getDesc() {
		return desc;
	}
	@Override
	public String toString() {
		return desc;
	}
}
