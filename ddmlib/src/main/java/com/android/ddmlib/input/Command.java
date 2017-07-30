package com.android.ddmlib.input;

public enum Command {

    GETEVENT("设备输入设备", "getevent"), GETEVENT_GETDEVICE("获取输入设备", GETEVENT, "-p"), GETEVENT_WHATCH_TEXT_EVENT("监听输入",
            GETEVENT, "-lt"), GETEVENT_WHATCH_RAW_EVENT("监听输入", GETEVENT, "-t"),

    SENDEVENT("发送输入时间", "sendevent"),

    INPUT("发送指令", "input"), INPUT_TAP("点击", "input tap"), INPUT_LTAP("长按", "intput swipe"), INPUT_SWIPE("滑动",
            "intput swipe");

    String cmd;
    String[] args;
    String desc;

    Command(String desc, String cmd, String... args) {
        this.cmd = cmd;
        this.args = args;
        this.desc = desc;
    }

    Command(String desc, Command command, String... args) {
        this.cmd = command.cmd;
        this.args = args;
        this.desc = desc;
    }

    public String getCmd() {
        return cmd;
    }

    public String[] getArgs(String... ext) {
        if (ext != null && ext.length > 0) {
            return concat(args, ext);
        }
        return args;
    }

    public String getDesc() {
        return desc;
    }

    private String[] concat(String[] a, String[] b) {
        String[] c = new String[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}