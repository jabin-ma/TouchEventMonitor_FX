package com.android.ddmlib.input.android;

import com.android.ddmlib.Log;

public class PowerEvent extends AbsMonitorEvent {
    @Override
    public void onCreate(RawEvent rawEvent) {
        Log.d("powerKey", "down");
    }

    @Override
    public void onSync(RawEvent rawEvent) {

    }

    @Override
    public void onPublish(RawEvent rawEvent) {
        closedProperty().setValue(true);
    }

    @Override
    public void onArgs(RawEvent rawEvent) {

    }

    @Override
    public String toString() {
        return "电源键";
    }
}
