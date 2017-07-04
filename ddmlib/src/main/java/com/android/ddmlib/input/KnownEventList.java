package com.android.ddmlib.input;

import com.android.ddmlib.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class KnownEventList {

    public List<PlainTextRawEvent> needwhatch = new ArrayList<PlainTextRawEvent>();

    public KnownEventList(String file) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            read(in);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void read(InputStream in) throws IOException {
        InputStreamReader ins = null;
        BufferedReader read = null;
        ins = new InputStreamReader(in);
        read = new BufferedReader(ins);
        String line;
        while ((line = read.readLine()) != null) {
            if (line.startsWith("#") || line.isEmpty())
                continue;
            needwhatch.add(new PlainTextRawEvent(line, null));
        }
        read.close();
        ins.close();
    }

    KnownEventList() {
        try {
            read(getClass().getResourceAsStream("KnownEventList.default"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean need(PlainTextRawEvent e) {
        return false;
    }

    public HandleType queryHandleType(PlainTextRawEvent in) {
        for (PlainTextRawEvent def : needwhatch) {
            if (def.equals(in)) {
                return def.getHandleType();
            }
        }
        return HandleType.UNKNOWN;
    }


    public String queryEventClass(PlainTextRawEvent in) {
        for (PlainTextRawEvent def : needwhatch) {
            if (def.equals(in)) {
                return def.getEventClass();
            }
        }
        return null;
    }

    public enum HandleType {
        EVENT_CREATE, EVENT_ARG_X, EVENT_ARG_Y, EVENT_PUBLISH, EVENT_SYNC, UNKNOWN;

        public static HandleType get(int type) {
            switch (type) {
                case 1:
                    return EVENT_CREATE;
                case 2:
                    return EVENT_ARG_X;
                case 3:
                    return EVENT_ARG_Y;
                case 4:
                    return EVENT_PUBLISH;
                case 5:
                    return HandleType.EVENT_SYNC;
                default:
                    Log.d("HandleType", "UnKnown HandleType:" + type);
                    return UNKNOWN;
            }
        }
    }
}
