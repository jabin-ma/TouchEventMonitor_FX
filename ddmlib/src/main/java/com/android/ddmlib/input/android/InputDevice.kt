package com.android.ddmlib.input.android

class InputDevice internal constructor(str: List<String>) {

    var devFile: String? = null
        private set
    var name: String? = null
        private set

    init {
        for (i in str.indices) {
            val s = str[i]
            if (s.contains(":")) {
                val split = s.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val name = split[0].trim { it <= ' ' }
                if (name.startsWith("input props")) {
                    println("input props==" + str[i + 1])
                }
                if (split.size < 2)
                    continue
                val value = split[1].trim { it <= ' ' }
                if (name.startsWith("add device")) {
                    devFile = value
                } else if (name.startsWith("name")) {
                    this.name = value
                }
            }
        }
    }


    override fun toString(): String {
        return "InputDevice \ndev=$devFile, \nname=$name"
    }

    companion object {

        val EVENT_RAW_MODE = true
    }

}