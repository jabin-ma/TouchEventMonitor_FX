package com.android.ddmlib.input.android

/**
 * Created by majipeng on 2017/6/22.
 */
interface EventMapper {

    fun processEvent(rawEvent: RawEvent): MonitorEvent?
}
