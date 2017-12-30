package com.android.ddmlib.input;

import com.android.ddmlib.utils.Log;

/**
 * 触摸点
 */
final class TouchPoint {

    private int x = Integer.MIN_VALUE, y = Integer.MIN_VALUE;
    private long timestamp = Integer.MIN_VALUE;
    private int mFlags = 0;

    static final int FLAG_X_SET = 1;
    static final int FLAG_Y_SET = 1 << 1;
    static final int FLAG_TIME_SET = 1 << 2;

    public int getX() {
        return x;
    }

    void setX(int x) {
        this.x = x;
        addFlags(FLAG_X_SET);
    }

    public int getY() {
        return y;
    }

    void setY(int y) {
        this.y = y;
        addFlags(FLAG_Y_SET);
    }

    @Override
    public String toString() {
        return x + " " + y;
    }

    public long getTimestamp() {
        return timestamp;
    }

    void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        addFlags(FLAG_TIME_SET);
    }

    final void addFlags(int flags) {
        this.mFlags |= flags;
    }

    boolean hasFlags(int flag) {
        return (mFlags & flag) == flag;
    }

    public String toArgs() {
        return x + " " + y;
    }

    /**
     * @param touchPoint
     */
    void fixPoint(TouchPoint touchPoint) {
        if (hasFlags(FLAG_X_SET | FLAG_Y_SET)) return;
        if (!hasFlags(FLAG_X_SET)) {
            setX(touchPoint.x);
        }
        if(!hasFlags(FLAG_Y_SET))
        {
            setY(touchPoint.y);
        }
    }
}
