package com.android.ddmlib.input;


public class EventCommand {

    private static final String TAG = "getevent";


    public int flags = 0;
    /**
     * 显示触摸板的事件，大部分手机不支持
     */
    public static final int FLAG_SHOW_TOUCHPAD = 1 << 1;
    /**
     * 显示时间戳
     */
    public static final int FLAG_SHOW_TIMESTAMP = 1 << 2;
    /**
     * 不换行
     */
    public static final int FLAG_DONT_NEWLINE = 1 << 3;
//    /**
//     * print all switch states
//     */
//    public static final int FLAG_PRINT_SWITCH_STATE=1<<4;
//
//    /**
//     * verbosity mask (errs=1, dev=2, name=4, info=8, vers=16, pos. events=32, props=64)
//     */
//    public static final int FLAG_VERBOSITY_MASK=1<<7;
//
//    /**
//     * show HID descriptor, if available
//     */
//    public static final int FLAG_SHOW_HID=1<<8;
//    /**
//     * show possible events (errs, dev, name, pos. events)
//     */
//    public static final int FLAG_SHOW_POSSIBLE=1<<9;
//    /**
//     * show all device info and possible events
//     */
//    public static final int FLAG_SHOW_ALLDEVICE=1<<10;
    /**
     * 事件可读化输出
     */
    public static final int FLAG_SHOW_LABEL = 1 << 11;
    /**
     * event开始不显示所有设备信息
     */
    public static final int FLAG_QUIET = 1 << 12;

    /**
     * show rate
     */
    public static final int FLAG_SHOW_RATE = 1 << 13;


    public static final int[] SUPPORTED = {
            FLAG_DONT_NEWLINE, FLAG_QUIET, FLAG_SHOW_LABEL, FLAG_SHOW_RATE, FLAG_SHOW_TIMESTAMP
    };


    public void setFlags(int flags) {
        this.flags = flags;
    }

    public void addFlags(int flags) {
        this.flags |= flags;
    }

    public void removeFlags(int flags) {
        this.flags &= ~flags;
    }

    public boolean hasFlags(int flag) {
        return (flags & flag) == flag;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(TAG);
        stringBuilder.append(" ");
        stringBuilder.append("-");
        for (int i : SUPPORTED) {
            if (hasFlags(i)) {
                stringBuilder.append(flagToStr(i));
            }
        }
        return stringBuilder.toString();
    }

    private String flagToStr(int flag) {
        String str = "";
        switch (flag) {
            case FLAG_DONT_NEWLINE:
                str = "n";
                break;
            case FLAG_QUIET:
                str = "q";
                break;
            case FLAG_SHOW_LABEL:
                str = "l";
                break;
            case FLAG_SHOW_RATE:
                str = "r";
                break;
            case FLAG_SHOW_TIMESTAMP:
                str = "t";
                break;
            case FLAG_SHOW_TOUCHPAD:
                str = "o";
                break;
        }
        return str;
    }
}
