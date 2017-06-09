package com.android.ddmlib.input;

import com.android.ddmlib.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class InputDeviceManager {

    private static final int TIMEOUT_SEC = 2;
    private static final boolean DEBUG = false;
    private IDevice dev;
    private HashMap<String, InputDevice> devList = new HashMap<>();

    public InputDeviceManager(IDevice dev) {
        super();
        this.dev = dev;
    }

    public List<InputDevice> getDevice(boolean force) {
        if (force || devList.isEmpty()) {
            try {
                dev.executeShellCommand(new OneLineReceiver() {
                    ArrayList<String> sb = new ArrayList<>();

                    @Override
                    public void processNewLines(String line) {
                        if (DEBUG) Log.d(getClass().getName(), line);
                        if (line.startsWith("add device")) {
                            if (sb.size() > 0) {
                                putDevice(sb);
                                sb.clear();
                            }
                        }
                        sb.add(line.trim());
//                    if(DEBUG)Log.d(getClass().getName(), sb.toString());
                    }

                    @Override
                    public void done() {
                        putDevice(sb);
                        sb.clear();
                    }
                }, TIMEOUT_SEC, TimeUnit.SECONDS, Command.GETEVENT_GETDEVICE);
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
        }
//        result.add(new InputDevice((List<String>) sb.clone(), dev));
        return new ArrayList<>(devList.values());
    }


    public void putDevice(ArrayList<String> sb) {
        InputDevice tempDev = new InputDevice((List<String>) sb.clone(), dev);
        if (!devList.containsKey(tempDev.getDevFile())) {
            devList.put(tempDev.getDevFile(), tempDev);
        }
    }
}