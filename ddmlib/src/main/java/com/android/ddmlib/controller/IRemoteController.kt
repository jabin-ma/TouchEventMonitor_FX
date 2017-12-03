package com.android.ddmlib.controller

/**
 * Created by majipeng on 2017/7/2.
 *
 * 远程控制接口,所有远程控制实体均实现该接口
 */
interface IRemoteController {
    fun create()
    fun touchDown(x: Int, y: Int)
    fun touchMove(x: Int, y: Int)
    fun touchUp(x: Int, y: Int)
    fun sleep(ms: Long)
    fun keyDown(key: KeyCode)
    fun keyUp(key: KeyCode)
    fun quit()
    fun done()
    fun keyClick(key: KeyCode)
    fun touchClick(x: Int, y: Int)

}