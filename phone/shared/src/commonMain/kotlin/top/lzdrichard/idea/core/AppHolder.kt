package top.lzdrichard.idea.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 应用级持有者（对齐 LoostoneCenter 的 base/common AppHolder 职责）
 *
 * 只放跨页面存活的东西：应用级 CoroutineScope。平台 Context 由各壳自己持有
 * （Android 在 AndroidApp，iOS 不需要）。
 */
object AppHolder {

    /** 应用级作用域：排队补送、登录态等不随页面销毁的任务用它 */
    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
