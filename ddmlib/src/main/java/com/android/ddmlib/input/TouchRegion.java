package com.android.ddmlib.input;

/**
 * Created by majipeng on 2017/7/4.
 * 点击事件活动区域,用来判断当前事件移动的距离
 */
public class TouchRegion {
    private int max_x = Integer.MIN_VALUE, max_y = Integer.MIN_VALUE;

    private int min_x = Integer.MAX_VALUE, min_y = Integer.MAX_VALUE;


    public void update(TouchPoint tp) {
        max_x = Math.max(tp.getX(), max_x);
        max_y = Math.max(tp.getY(), max_y);
        min_x = Math.min(tp.getX(), min_x);
        min_y = Math.min(tp.getY(), max_y);
    }

    public int offsetX() {
        return max_x - min_x;
    }

    public int offsetY() {
        return max_y - min_y;
    }
}
