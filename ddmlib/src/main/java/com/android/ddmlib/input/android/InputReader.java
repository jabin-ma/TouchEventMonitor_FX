package com.android.ddmlib.input.android;

import com.android.ddmlib.Log;
import com.android.ddmlib.input.KnownEventList;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputReader {


    public InputReader(EventHub eventHub) {
        while (true) {
            try {
                RawEvent rawEvent = eventHub.getEvent();
                Log.d("inputread", rawEvent.toString());
//                rawEvent.
                KnownEventList l;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
