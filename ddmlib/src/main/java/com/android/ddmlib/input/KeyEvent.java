package com.android.ddmlib.input;

import com.android.ddmlib.controller.IRemoteController;
import com.android.ddmlib.controller.KeyCode;
import com.android.ddmlib.utils.Log;

/**
 * 按键事件
 */
public class KeyEvent extends AbsMonitorEvent {

    @Override
    public void onCreate(IRawEvent rawEvent) {
        super.onCreate(rawEvent);
        eventTypeProperty().setValue("实体按键");
        eventDescProperty().setValue(rawEvent.getCode());
        addFlags(FLAG_WAIT_SYNC_CREATE);
    }

    @Override
    public void onSync(IRawEvent rawEvent) {
        Log.d("KEY", "onSync");
        if (hasFlags(FLAG_WAIT_SYNC_CREATE)) {
            removeFlags(FLAG_WAIT_SYNC_CREATE);
        }
        if (hasFlags(FLAG_WAIT_SYNC_PUBLISH)) {
            removeFlags(FLAG_WAIT_SYNC_PUBLISH);
            publishProperty().setValue(true);
        }
    }

    @Override
    public void onPublish(IRawEvent rawEvent) {
        super.onPublish(rawEvent);
        addFlags(FLAG_WAIT_SYNC_PUBLISH);
    }

    @Override
    public void onArgs(IRawEvent rawEvent) {

    }

    @Override
    public void processController(IRemoteController controller) {
        KeyCode code = KeyCode.valueOf(eventDescProperty().get().replace("KEY_", ""));
        if (code == null) {

        } else {
            controller.keyClick(code);
        }
    }

    @Override
    public boolean fixEvent(MonitorEvent monitorEvent) {
        return true;
    }
}
