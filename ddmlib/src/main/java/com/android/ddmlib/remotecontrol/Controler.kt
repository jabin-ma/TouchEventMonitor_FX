package com.android.ddmlib.remotecontrol

import sun.tools.tree.LongExpression

/**
 * Created by majipeng on 2017/7/2.
 */
interface Controler {


    fun touchDown(x:Int,y:Int)
    fun touchMove(x:Int,y:Int)
    fun touchUp(x:Int,y:Int)
    fun sleep(ms:Long)

    fun keyDown(key:String)
    fun keyUp(key:String)

}