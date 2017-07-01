package com.android.ddmlib.input.android

import java.util.HashMap

/**
 * Created by majipeng on 2017/6/19.
 */
class InputReader internal constructor(private val eventHub: EventHub) {

    internal var mappers = HashMap<String, EventMapper>()

    internal var knownEventList = KnownEventList()

    /**
     * 非异步
     */
    fun readBySync(): MonitorEvent? {
        try {
            val rawEvent = eventHub.event
            var mapper: EventMapper? = mappers[rawEvent.devFile]
            if (mapper == null) {
                mapper = EventMapperImpl(knownEventList)
                mappers.put(rawEvent.devFile, mapper)
            }
            return mapper.processEvent(rawEvent)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return null
    }


}
