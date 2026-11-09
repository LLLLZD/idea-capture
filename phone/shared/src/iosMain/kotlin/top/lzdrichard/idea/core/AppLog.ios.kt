package top.lzdrichard.idea.core

import platform.Foundation.NSLog

actual fun platformLogLine(level: String, tag: String, message: String) {
    NSLog("[" + level + "][" + tag + "] " + message)
}
