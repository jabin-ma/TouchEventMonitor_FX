package com.android.ddmlib.input;

/**
 * Created by majipeng on 2017/6/22.
 * 事件映射器,将rawevent准确映射为本地事件
 */
public interface EventMapper {
    MonitorEvent mappingEvent(IRawEvent rawEvent);
}
