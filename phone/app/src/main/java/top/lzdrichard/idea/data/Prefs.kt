package top.lzdrichard.idea.data

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("idea", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(value) = sp.edit().putString("server_url", value).apply()

    var asrUrl: String
        get() = sp.getString("asr_url", DEFAULT_ASR) ?: DEFAULT_ASR
        set(value) = sp.edit().putString("asr_url", value).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(value) = sp.edit().putString("token", value).apply()

    var displayName: String
        get() = sp.getString("display_name", "") ?: ""
        set(value) = sp.edit().putString("display_name", value).apply()

    /** 断网时排队的提交，JSON 数组字符串。 */
    var pending: String
        get() = sp.getString("pending", "[]") ?: "[]"
        set(value) = sp.edit().putString("pending", value).apply()

    fun clearLogin() {
        sp.edit().remove("token").apply()
    }

    companion object {
        const val DEFAULT_SERVER = "https://api-idea.lzdrichard.top"
        const val DEFAULT_ASR = "http://192.168.2.125:8030/v1"
    }
}
