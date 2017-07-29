package com.android.ddmlib.input;

/**
 * Created by majipeng on 2017/6/22.
 */
public interface EventMapper {
    public MonitorEvent processEvent(PlainTextRawEvent rawEvent);
}
