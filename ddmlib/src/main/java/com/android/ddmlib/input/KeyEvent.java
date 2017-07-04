package com.android.ddmlib.input;

public class KeyEvent extends AbsMonitorEvent {

    @Override
    public void onCreate(PlainTextRawEvent rawEvent) {
           super.onCreate(rawEvent);
           eventTypeProperty().setValue("实体按键");
           eventDescProperty().setValue(rawEvent.getCode());
    }

    @Override
    public void onSync(PlainTextRawEvent rawEvent) {

    }

    @Override
    public void onPublish(PlainTextRawEvent rawEvent) {
        super.onPublish(rawEvent);
        closedProperty().setValue(true);
    }

    @Override
    public void onArgs(PlainTextRawEvent rawEvent) {

    }

}
