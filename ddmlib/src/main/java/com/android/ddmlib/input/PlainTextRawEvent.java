package com.android.ddmlib.input;

/**
 * as such getevent -lt
 */
public class PlainTextRawEvent implements IRawEvent {

    private When when = null;
    private String type = null, code = null, value = null, devFile;
    private KnownEventList.HandleType handleType;

    private String eventClass;


    public PlainTextRawEvent(String str, String devFile) {
        setDevFile(devFile);
        String[] args = str.replaceAll("[\\[\\]]", "").replace(":"," ").replaceAll(" +", " ").trim().split(" ");
        for (int i = 0; i < args.length; i++) {
            set(i, args[i]);
        }
    }

    public void set(int index, String value) {
        switch (index) {
            case 0:
                setWhen(new When(value));
                break;
            case 1:
                setDevFile(value);
                break;
            case 2:
                setType(value);
                break;
            case 3:
                setCode(value);
                break;
            case 4:
                this.value = value;
                setValue(this.value);
                break;
            case 5:
                setHandleType(KnownEventList.HandleType.valueOf(value));
                break;
            case 6:
                setEventClass(value);
            default:
                break;
        }
    }

    public String getOwner() {
        return devFile;
    }

    public void setDevFile(String devFile) {
        this.devFile = devFile;
    }

    @Override
    public String toString() {
        return "devfile=" + devFile + " when=" + when + " type=" + type + " code=" + code + " value=" + value;
    }

    static boolean ignore(String str) {
        return "ignore".equals(str);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PlainTextRawEvent other = (PlainTextRawEvent) obj;
        // // when
        // if (!ignore(when.toString()) && !ignore(other.when.toString())) {
        // if (!when.equals(other.when))
        // return false;
        // }
        // type
        if (!ignore(type) && !ignore(other.type)) {
            if (!type.equals(other.type))
                return false;
        }
        // name
        if (!ignore(code) && !ignore(other.code)) {
            if (!code.equals(other.code))
                return false;
        }
        // value
        if (!ignore(value) && !ignore(other.value)) {
            if (!value.equals(other.value))
                return false;
        }
        return true;
    }


    public String getEventClass() {
        return eventClass;
    }

    public void setEventClass(String eventClass) {
        this.eventClass = eventClass;
    }

    @Override
    public When getWhen() {
        return when;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    public KnownEventList.HandleType getHandleType() {
        return handleType;
    }

    public void setHandleType(KnownEventList.HandleType handleType) {
        this.handleType = handleType;
    }

    void setWhen(When when) {
        this.when = when;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
