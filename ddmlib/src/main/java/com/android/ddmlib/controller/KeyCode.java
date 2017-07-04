package com.android.ddmlib.controller;

/**
 * Created by majipeng on 2017/7/3.
 */
public enum KeyCode {
    MENU("KEYCODE_MENU"), BACK("KEYCODE_BACK"),POWER("KEYCODE_POWER"),APPSELECT("KEYCODE_APP_SWITCH");
    String code;
    KeyCode(String code) {
        this.code = code;
    }


    public String getCode() {
        return code;
    }
}
