package com.android.ddmlib.input.android

interface IEvent {

    val time: Time


    var type: String


    var code: String


    var value: String
}
