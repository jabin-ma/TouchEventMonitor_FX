package com.android.ddmlib.input.android;

/**
 * Created by majipeng on 2017/6/21.
 */
public interface MonitorEvent {


    public void onCreate(RawEvent rawEvent);

    public void onSync(RawEvent rawEvent);
    void onPublish(RawEvent rawEvent);
    void onArgs(RawEvent rawEvent);


}
