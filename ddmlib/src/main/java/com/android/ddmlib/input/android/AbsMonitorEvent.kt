package com.android.ddmlib.input.android

import com.android.ddmlib.Log
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue

/**
 * Created by majipeng on 2017/6/30.
 */
abstract class AbsMonitorEvent : MonitorEvent, ChangeListener<Boolean> {
    private var dispatchCount = 0
    private val eventType = SimpleStringProperty()
    private val eventDesc = SimpleStringProperty()
    private val eventDur = SimpleStringProperty()
    private val inputDevice = SimpleStringProperty()
    private val closed = SimpleBooleanProperty()
    private var begin: RawEvent? = null
    private var end: RawEvent? = null

    override fun onCreate(rawEvent: RawEvent) {
        begin = rawEvent
        closed.addListener(this)
        Log.d("absMonitor", "create:" + begin!!.time.ms)
    }

    override fun onPublish(rawEvent: RawEvent) {
        end = rawEvent
        Log.d("absMonitor", "onPublish:" + end!!.time.ms)
    }

    override fun changed(observable: ObservableValue<out Boolean>, oldValue: Boolean?, newValue: Boolean?) {
        //closed
        Log.d("absMonitor", "close change:" + (end!!.time.ms - begin!!.time.ms))
        closed.removeListener(this)
        eventDur.value = (end!!.time.ms - begin!!.time.ms).toString() + "ms"
    }

    fun getEventType(): String {
        return eventType.get()
    }

    override fun eventTypeProperty(): SimpleStringProperty {
        return eventType
    }

    override fun eventDescProperty(): SimpleStringProperty {
        return eventDesc
    }

    override fun eventDurProperty(): SimpleStringProperty {
        return eventDur
    }

    override fun closedProperty(): SimpleBooleanProperty {
        return closed
    }

    override fun inputDeviceProperty(): SimpleStringProperty {
        return inputDevice
    }

    override fun setDispatched() {
        dispatchCount++
    }

    override fun dispatchCount(): Int {
        return dispatchCount
    }
}
