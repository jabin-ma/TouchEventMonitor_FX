package com.android.ddmlib.controller;

import org.jetbrains.annotations.NotNull;

/**
 * Created by majipeng on 2017/7/4.
 */
public abstract class SimpleRemoteController implements IRemoteController {

    @Override
    public void keyClick(@NotNull KeyCode key) {
        keyDown(key);
        sleep(100);
        keyUp(key);
    }

    @Override
    public void touchClick(int x, int y) {
        touchDown(x, y);
        sleep(100);
        touchUp(x, y);
    }
}
