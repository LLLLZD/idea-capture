package top.lzdrichard.idea.presentation.theme

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距 Token（结构照搬 LoostoneCenter：M3 无内建间距体系 → 自定义 CompositionLocal 下发）
 *
 * 经 LocalAppSpacing 由 IdeaCaptureTheme 提供；子树/预览/测试可整体覆盖。
 */
data class AppSpacingTokens(
    /** 超超小间距：2dp（边框宽度等微调） */
    val xxs: Dp = 2.dp,
    /** 超小间距：4dp */
    val xs: Dp = 4.dp,
    /** 小间距：8dp */
    val sm: Dp = 8.dp,
    /** 中间距：12dp */
    val md: Dp = 12.dp,
    /** 大间距：16dp */
    val lg: Dp = 16.dp,
    /** 超大间距（紧凑）：20dp */
    val xlSm: Dp = 20.dp,
    /** 超大间距：24dp */
    val xl: Dp = 24.dp,
    /** 超超大间距：28dp（页面水平边距） */
    val xxl: Dp = 28.dp,
    /** 超大间距（加大）：32dp */
    val xlLg: Dp = 32.dp,
)

@Composable
fun Dp.ToVerticalSpace() {
    Spacer(modifier = Modifier.height(this))
}

val LocalAppSpacing = staticCompositionLocalOf { AppSpacingTokens() }

/** Home 顶栏高度（沉浸式布局下发）：页面背景铺满、内容用这个值让开顶栏。 */
val LocalTopBarInset = staticCompositionLocalOf { 0.dp }

/** 页面访问入口：AppSpacing.current.lg —— 禁止绕过 Local 直连常量 */
object AppSpacing {
    val current: AppSpacingTokens
        @Composable @ReadOnlyComposable get() = LocalAppSpacing.current
}
