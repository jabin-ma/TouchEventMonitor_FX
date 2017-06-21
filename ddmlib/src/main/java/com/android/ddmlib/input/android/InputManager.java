package com.android.ddmlib.input.android;

import com.android.ddmlib.IDevice;

/**
 * Created by majipeng on 2017/6/19.
 */
public class InputManager {
       IDevice mAndroidDevice;
       EventHub eventHub;
       InputReader inputReader;
       InputDispatcher inputDispatcher;

    public InputManager(IDevice mAndroidDevice) {
        this.mAndroidDevice = mAndroidDevice;
        eventHub=new EventHub(this);
        inputReader= new InputReader(eventHub);
    }

    public IDevice getAndroidDevice() {
        return mAndroidDevice;
    }

}
