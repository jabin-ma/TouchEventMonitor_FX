package com.android.ddmlib.input;

/**
 * Created by majipeng on 2017/6/22.
 */
public interface EventMapper {
     MonitorEvent processEvent(PlainTextRawEvent rawEvent);
}
