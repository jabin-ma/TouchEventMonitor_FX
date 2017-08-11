package com.android.ddmlib.input;

/**
 * 原始事件的时间
 */
public class When {
    long ms;

    public When(String str) {
        str = str.trim().replace(" +", "");
        if (PlainTextRawEvent.ignore(str) || !str.contains("."))
            return;
        String[] sms = str.split("\\.");
        ms = Long.parseLong(sms[0] + sms[1].substring(0, 3));
    }

    @Override
    public String toString() {
        return ms + "ms";
    }
}
