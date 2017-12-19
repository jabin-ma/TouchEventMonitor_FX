package com.android.ddmlib.input;

import com.android.ddmlib.controller.IRemoteController;
import com.android.ddmlib.controller.KeyCode;

/**
 * 按键事件
 */
public class KeyEvent extends AbsMonitorEvent {

    @Override
    public void onCreate(IRawEvent rawEvent) {
        super.onCreate(rawEvent);
        eventTypeProperty().setValue("实体按键");
        eventDescProperty().setValue(rawEvent.getCode());
    }

    @Override
    public void onSync(IRawEvent rawEvent) {

    }

    @Override
    public void onPublish(IRawEvent rawEvent) {
        super.onPublish(rawEvent);
        publishProperty().setValue(true);
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

}
