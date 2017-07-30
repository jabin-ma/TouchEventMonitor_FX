package com.android.ddmlib.input

import com.android.ddmlib.Log
import com.android.ddmlib.utils.d

/**
 * Created by majipeng on 2017/6/22.
 */
class EventMapperImpl(private val knownEventList: KnownEventList) : EventMapper {


    private var monitorEvent: MonitorEvent? = null

    /**
     * 处理rawevent，当完整记录一次事件时，将返回该完整的事件,单次驱动!
     */
    override fun processEvent(rawEvent: PlainTextRawEvent): MonitorEvent? {
        val handleType = knownEventList.queryHandleType(rawEvent)
        rawEvent.handleType = handleType
        rawEvent.eventClass = knownEventList.queryEventClass(rawEvent)
        when (handleType) {
            KnownEventList.HandleType.EVENT_CREATE -> {
                monitorEvent = Class.forName(rawEvent.eventClass).newInstance() as MonitorEvent?;
                if (monitorEvent == null) {
                    if (DEBUG) d("create obj failed--${rawEvent.devFile}")
                    return null;
                }

                monitorEvent?.inputDeviceProperty()?.value = rawEvent.devFile
                monitorEvent?.onCreate(rawEvent)
            }
            KnownEventList.HandleType.EVENT_ARG_X, KnownEventList.HandleType.EVENT_ARG_Y -> {
                monitorEvent?.onArgs(rawEvent)
            }
            KnownEventList.HandleType.EVENT_SYNC -> {
                monitorEvent?.onSync(rawEvent)
                if (monitorEvent != null && monitorEvent!!.closedProperty().value && monitorEvent!!.dispatchCount() == 0) {
                    return monitorEvent;
                }
            }
            KnownEventList.HandleType.EVENT_PUBLISH -> {
                monitorEvent?.onPublish(rawEvent)
            }
            KnownEventList.HandleType.UNKNOWN -> {
                if (DEBUG) Log.d(TAG, "UnKnown event..$rawEvent")
            }
        }

        return null;
    }


    companion object {
        private val TAG = "EventMapperImpl"
        private val DEBUG = false
    }


}
