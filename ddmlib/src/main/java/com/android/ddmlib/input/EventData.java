package com.android.ddmlib.input;

import com.android.ddmlib.Log;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

public class EventData {
    private final SimpleStringProperty eventType = new SimpleStringProperty();
    private final SimpleStringProperty eventDesc = new SimpleStringProperty();
    private final SimpleStringProperty eventDur = new SimpleStringProperty();
    private final SimpleDoubleProperty progress = new SimpleDoubleProperty();

    /**
     * @return the progress
     */
    public double getProgress() {
        Log.d("eventData", "getProgress");
        return progress.get();
    }

    /**
     * @param progress the progress to set
     */
    public void setProgress(double progress) {
        this.progress.set(progress);
    }

    public SimpleDoubleProperty progressProperty() {
        Log.d("eventData", "progressProperty");
        return progress;
    }

    public String getEventType() {

        Log.d("eventData", "getEventType");
        return eventType.get();
    }

    public String getEventDesc() {

        Log.d("eventData", "getEventDesc");
        return eventDesc.get();
    }

    public String getEventDur() {
        Log.d("eventData", "getEventDur");
        return eventDur.get();
    }

    public void setEventType(String value) {
        eventType.set(value);
    }

    public void setEventDesc(String value) {
        eventDesc.set(value);
    }

    public void setEventDur(String value) {
        eventDur.set(value);
    }

    public SimpleStringProperty eventTypeProperty() {
        return eventType;
    }

    public SimpleStringProperty eventDescProperty() {
        return eventDesc;
    }

    public SimpleStringProperty eventDurProperty() {
        return eventDur;
    }

}