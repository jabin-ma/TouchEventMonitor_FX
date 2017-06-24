package com.android.ddmlib.input.android;

/**
 * Created by majipeng on 2017/6/21.
 */
public interface MonitorEvent {
    void onCreate(RawEvent rawEvent);

    void onSync(RawEvent rawEvent);

    void onPublish(RawEvent rawEvent);

    void onArgs(RawEvent rawEvent);

    boolean isClosed();

    void setInputDevice(String inputDevice);

    String getInputDevice();
}
