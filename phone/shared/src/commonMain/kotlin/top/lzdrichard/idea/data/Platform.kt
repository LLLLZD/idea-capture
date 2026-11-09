package top.lzdrichard.idea.data

expect fun nowMillis(): Long

/** client_id 由客户端生成，服务端用 (user_id, client_id) 做幂等键。 */
expect fun randomId(): String

/** 客户端私有文件：排队的录音要能在重启、断网后还活着。 */
expect object PlatformFile {
    fun privatePath(fileName: String): String
    /** 复制成功返回目标路径，失败返回 null。 */
    fun copyTo(sourcePath: String, destPath: String): String?
    fun delete(path: String)
    fun exists(path: String): Boolean
}

/** 录音输出文件放缓存目录，转写成功后删掉。 */
expect fun newRecordingPath(): String
