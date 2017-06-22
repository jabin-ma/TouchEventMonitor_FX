package com.android.ddmlib.input.android;

import java.util.HashMap;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputReader {


    HashMap<String,EventMapper> mappers=new HashMap<>();


    KnownEventList knownEventList=new KnownEventList();

    public InputReader(EventHub eventHub) {
        while (true) {
            try {
                RawEvent rawEvent = eventHub.getEvent();
                EventMapper mapper=mappers.get(rawEvent.getDevFile());
                if(mapper==null){
                    mapper=new EventMapperImpl(knownEventList);
                    mappers.put(rawEvent.getDevFile(),mapper);
                }
                mapper.processEvent(rawEvent);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
