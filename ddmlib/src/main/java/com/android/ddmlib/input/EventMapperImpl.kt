package com.android.ddmlib.input

import com.android.ddmlib.utils.Log
import com.android.ddmlib.utils.d

/**
 * Created by majipeng on 2017/6/22.
 * 实现映射器接口,按照给定的映射规则生成本地事件
 */
class EventMapperImpl(private val knownEventList: KnownEventList) : EventMapper {


    private var monitorEvent: MonitorEvent? = null
    private var prevEvent: MonitorEvent? = null

    /**
     * 处理rawevent，当完整记录一次事件时，将返回该完整的事件,单次驱动!
     */
    override fun mappingEvent(rawEvent: IRawEvent): MonitorEvent? {
        val handleType = knownEventList.queryHandleType(rawEvent)
        rawEvent.handleType = handleType
        rawEvent.eventClass = knownEventList.queryEventClass(rawEvent)
        when (handleType) {
            KnownEventList.HandleType.EVENT_CREATE -> {
                prevEvent=monitorEvent
                monitorEvent = Class.forName(rawEvent.eventClass).newInstance() as MonitorEvent?
                if (monitorEvent == null) {
                    if (DEBUG) d("create obj failed--${rawEvent.owner}")
                    return null
                }
                monitorEvent?.inputDeviceProperty()?.value = rawEvent.owner
                monitorEvent?.onCreate(rawEvent)
            }
            KnownEventList.HandleType.EVENT_ARG_X, KnownEventList.HandleType.EVENT_ARG_Y -> {
                monitorEvent?.onArgs(rawEvent)
            }
            KnownEventList.HandleType.EVENT_SYNC -> {
                monitorEvent?.onSync(rawEvent)
                if (monitorEvent != null && monitorEvent!!.publishProperty().value && monitorEvent!!.dispatchCount() == 0) {
                    if(monitorEvent!!.hasFlags(TouchEvent.FLAG_NEED_FIX))
                    {
                      monitorEvent!!.fixEvent(prevEvent)
                    }
                    return monitorEvent
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
