package top.lzdrichard.idea.data

/** 平台本地键值存储（Android SharedPreferences、iOS NSUserDefaults）。 */
expect class PlatformStore() {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class Prefs(private val store: PlatformStore = PlatformStore()) {

    var serverUrl: String
        get() = store.getString("server_url") ?: DEFAULT_SERVER
        set(value) = store.putString("server_url", value)

    var asrUrl: String
        get() = store.getString("asr_url") ?: DEFAULT_ASR
        set(value) = store.putString("asr_url", value)

    var token: String?
        get() = store.getString("token")
        set(value) {
            if (value == null) store.remove("token") else store.putString("token", value)
        }

    var displayName: String
        get() = store.getString("display_name") ?: ""
        set(value) = store.putString("display_name", value)

    /** 转写路线：auto / native / api。 */
    var asrRoute: String
        get() = store.getString("asr_route") ?: "auto"
        set(value) = store.putString("asr_route", value)

    /** 断网时排队的提交，JSON 数组字符串。 */
    var pending: String
        get() = store.getString("pending") ?: "[]"
        set(value) = store.putString("pending", value)

    fun clearLogin() {
        store.remove("token")
    }

    companion object {
        const val DEFAULT_SERVER = "https://api-idea.lzdrichard.top"
        const val DEFAULT_ASR = "https://api-idea.lzdrichard.top/asr/v1"
    }
}
