package com.android.ddmlib.controller;

/**
 * Created by majipeng on 2017/7/3.
 */
public enum KeyCode {
    MENU("menu"), BACK("back");
    String code;
    KeyCode(String code) {
        this.code = code;
    }


    public String getCode() {
        return code;
    }
}
