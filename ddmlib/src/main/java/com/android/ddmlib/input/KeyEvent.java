package com.android.ddmlib.input;

public class KeyEvent extends AbsMonitorEvent {

    @Override
    public void onCreate(RawEvent rawEvent) {
           super.onCreate(rawEvent);
           eventTypeProperty().setValue("实体按键");
           eventDescProperty().setValue(rawEvent.getCode());
    }

    @Override
    public void onSync(RawEvent rawEvent) {

    }

    @Override
    public void onPublish(RawEvent rawEvent) {
        super.onPublish(rawEvent);
        closedProperty().setValue(true);
    }

    @Override
    public void onArgs(RawEvent rawEvent) {

    }
}
