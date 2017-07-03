package com.android.ddmlib.remotecontrol

/**
 * Created by majipeng on 2017/7/2.
 */
interface Controler {
    fun create()
    fun touchDown(x: Int, y: Int)
    fun touchMove(x: Int, y: Int)
    fun touchUp(x: Int, y: Int)
    fun sleep(ms: Long)
    fun keyDown(key: KeyCode)
    fun keyUp(key: KeyCode)

}