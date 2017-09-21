package com.android.ddmlib.utils


fun Any.d(msg: String) {
    Log.d(this::class.simpleName, msg)
}