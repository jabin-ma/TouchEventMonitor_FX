package com.android.ddmlib.input.android;

import com.android.ddmlib.Log;

import java.util.HashMap;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputReader {

    HashMap<String, EventMapper> mappers = new HashMap<>();

    KnownEventList knownEventList = new KnownEventList();
    private EventHub eventHub;


    public InputReader(EventHub eventHub) {
        this.eventHub = eventHub;
    }

    /**
     * 非异步
     */
    public MonitorEvent readBySync() {
        try {
            RawEvent rawEvent = eventHub.getEvent();
            EventMapper mapper = mappers.get(rawEvent.getDevFile());
            if (mapper == null) {
                mapper = new EventMapperImpl(knownEventList);
                mappers.put(rawEvent.getDevFile(), mapper);
            }
            return mapper.processEvent(rawEvent);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }


}
