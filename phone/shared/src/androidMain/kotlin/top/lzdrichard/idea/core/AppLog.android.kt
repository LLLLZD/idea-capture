package top.lzdrichard.idea.core

import android.util.Log

actual fun platformLogLine(level: String, tag: String, message: String) {
    when (level) {
        "W" -> Log.w(tag, message)
        "E" -> Log.e(tag, message)
        else -> Log.i(tag, message)
    }
}
