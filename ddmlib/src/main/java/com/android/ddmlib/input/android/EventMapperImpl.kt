package com.android.ddmlib.input.android

import com.android.ddmlib.Log

/**
 * Created by majipeng on 2017/6/22.
 */
class EventMapperImpl(private val knownEventList: KnownEventList) : EventMapper {


    private var monitorEvent: MonitorEvent? = null
    override fun processEvent(rawEvent: RawEvent) {
        //        KnownEventList.HandleType handleType=rawEvent.getHandleType();
        val handleType = knownEventList.queryHandleType(rawEvent)
        rawEvent.handleType = handleType
        when (handleType) {
            KnownEventList.HandleType.EVENT_CREATE -> {
                monitorEvent = MonitorEventItem()
                monitorEvent?.onCreate(rawEvent)
            }
            KnownEventList.HandleType.EVENT_ARG_X, KnownEventList.HandleType.EVENT_ARG_Y -> monitorEvent?.onArgs(rawEvent)
            KnownEventList.HandleType.EVENT_SYNC -> monitorEvent?.onSync(rawEvent)
            KnownEventList.HandleType.EVENT_PUBLISH -> monitorEvent?.onPublish(rawEvent)
            KnownEventList.HandleType.UNKNOWN -> Log.d(TAG, "UnKnown event..$rawEvent")
        }

//        Log.d(TAG, "processEvent:" + rawEvent.toString())
    }

    companion object {
        private val TAG = "EventMapperImpl"
        private val DEBUG = false
    }
}
