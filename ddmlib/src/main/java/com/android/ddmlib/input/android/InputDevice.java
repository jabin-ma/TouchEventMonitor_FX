package com.android.ddmlib.input.android;

import java.util.List;

public class InputDevice {

    public static final boolean EVENT_RAW_MODE = true;

    private String devFile, name;

    InputDevice(List<String> str) {
        super();
        for (int i = 0; i < str.size(); i++) {
            String s = str.get(i);
            if (s.contains(":")) {
                String[] split = s.split(":");
                String name = split[0].trim();
                if (name.startsWith("input props")) {
                    System.out.println("input props==" + str.get(i + 1));
                }
                if (split.length < 2)
                    continue;
                String value = split[1].trim();
                if (name.startsWith("add device")) {
                    devFile = value;
                } else if (name.startsWith("name")) {
                    this.name = value;
                }
            }
        }
    }


    @Override
    public String toString() {
        return "InputDevice \ndev=" + devFile + ", \nname=" + name;
    }

    public String getDevFile() {
        return devFile;
    }

    public String getName() {
        return name;
    }

}