package top.lzdrichard.idea

import android.content.Context

/** 平台层拿不到 Context，由 androidApp 的 MainActivity 在启动时塞进来。 */
object AndroidApp {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}
