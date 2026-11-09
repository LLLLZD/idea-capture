package top.lzdrichard.idea.core

/**
 * 统一日志门面（对齐 LoostoneCenter 的 base/log：先保留最小可用形态）
 *
 * 平台差异只在打印通道：Android 走 Logcat，iOS 走 NSLog。
 */
expect fun platformLogLine(level: String, tag: String, message: String)

object AppLog {

    fun i(tag: String, message: String) = platformLogLine("I", tag, message)

    fun w(tag: String, message: String) = platformLogLine("W", tag, message)

    fun e(tag: String, message: String) = platformLogLine("E", tag, message)
}
