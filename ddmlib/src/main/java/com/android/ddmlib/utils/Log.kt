package com.android.ddmlib.utils

import com.android.ddmlib.Log


fun Any.d(msg: String) {
    Log.d(this::class.simpleName, msg)
}