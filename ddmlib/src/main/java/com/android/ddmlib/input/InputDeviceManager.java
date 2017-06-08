package com.android.ddmlib.input;

import com.android.ddmlib.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class InputDeviceManager {

    private static final int TIMEOUT_SEC = 2;
    private static final boolean DEBUG=false;
    private IDevice dev;

    public InputDeviceManager(IDevice dev) {
        super();
        this.dev = dev;
    }

    @SuppressWarnings("unchecked")
    public List<InputDevice> getDevice() {
        List<InputDevice> result = new ArrayList<>();
        ArrayList<String> sb = new ArrayList<>();
        try {
            dev.executeShellCommand(new OneLineReceiver() {
                @Override
                public void processNewLines(String line) {
                    if(DEBUG)Log.d(getClass().getName(), line);
                    if (line.startsWith("add device")) {
                        if (sb.size() > 0) {
                            result.add(new InputDevice((List<String>) sb.clone(), dev));
                            sb.clear();
                        }
                    }
                    sb.add(line.trim());
                    if(DEBUG)Log.d(getClass().getName(), sb.toString());
                }
            },TIMEOUT_SEC,TimeUnit.SECONDS,Command.GETEVENT_GETDEVICE);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (AdbCommandRejectedException e) {
            e.printStackTrace();
        } catch (ShellCommandUnresponsiveException e) {
//            e.printStackTrace();
        } catch (TimeoutException e) {
//            e.printStackTrace();
        } finally {

        }
        result.add(new InputDevice((List<String>) sb.clone(), dev));
        return result;
    }
}