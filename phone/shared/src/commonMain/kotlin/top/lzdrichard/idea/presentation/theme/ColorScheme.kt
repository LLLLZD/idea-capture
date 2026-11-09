package top.lzdrichard.idea.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用 ColorScheme 定义（结构照搬 LoostoneCenter，品牌值换成「随记」）。
 *
 * 所有页面/组件的颜色必须从 MaterialTheme.colorScheme 获取，禁止硬编码；
 * 唯一定义处是本文件与 AppTheme.kt。
 *
 * 角色定义（Material 3）：primary=主要交互/选中；secondary=低强调容器；error=破坏性动作。
 * 标「推导」= 无设计稿依据、按角色从基色推导，后续设计稿补充后再校准。
 */

// ─── Light 主题（默认：暖沙底 + 墨绿主色，沿用 v1 客户端观感）───

val LightColorScheme = lightColorScheme(
    // 背景 #F6F2EA 暖沙；正文 #1F2421 墨黑
    background = Color(0xFFF6F2EA),
    onBackground = Color(0xFF1F2421),

    // 表面：卡片白、输入框白、区块底 #EDE7DA（原 Markdown 代码块底色）
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF1F2421),
    surfaceVariant = Color(0xFFEDE7DA),
    onSurfaceVariant = Color(0xFF5C635E),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFDF8),
    surfaceContainer = Color(0xFFF6F2EA),
    surfaceContainerHigh = Color(0xFFEDE7DA),
    surfaceContainerHighest = Color(0xFFE3DCCD),

    // 主色：墨绿 #2F6F4E（原按钮/强调色）
    primary = Color(0xFF2F6F4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x1A2F6F4E),          // 推导：主色 10% 玻璃底
    onPrimaryContainer = Color(0xFF12341F),        // 推导

    // 次色：暖棕灰，用于弱强调
    secondary = Color(0xFF7A6A52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE7DA),
    onSecondaryContainer = Color(0xFF3A3226),

    // 第三色与主色同值（M3 组件自然回落主色系）
    tertiary = Color(0xFF2F6F4E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0x1A2F6F4E),
    onTertiaryContainer = Color(0xFF12341F),

    // 错误/失败
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0x1AB3261E),
    onErrorContainer = Color(0xFF5F1410),

    outline = Color(0xFF9A9488),
    outlineVariant = Color(0xFFD8D0C0),

    inverseSurface = Color(0xFF1F2421),
    inverseOnSurface = Color(0xFFF6F2EA),
    inversePrimary = Color(0xFF8FC7A8),
    scrim = Color(0xCC000000),
)

// ─── Dark 主题（结构预留：暖沙反转 + 主色提亮保对比；设计稿未出，值属推导）───

val DarkColorScheme = darkColorScheme(
    background = Color(0xFF171A18),
    onBackground = Color(0xFFF3EFE6),
    surface = Color(0xFF1F2421),
    onSurface = Color(0xFFF3EFE6),
    surfaceVariant = Color(0xFF2A302C),
    onSurfaceVariant = Color(0xFFB9B3A6),
    surfaceContainerLowest = Color(0xFF121514),
    surfaceContainerLow = Color(0xFF1F2421),
    surfaceContainer = Color(0xFF232826),
    surfaceContainerHigh = Color(0xFF2A302C),
    surfaceContainerHighest = Color(0xFF343A36),

    primary = Color(0xFF8FC7A8),
    onPrimary = Color(0xFF0B2015),
    primaryContainer = Color(0x338FC7A8),
    onPrimaryContainer = Color(0xFFD6EFE0),

    secondary = Color(0xFFCBBFA6),
    onSecondary = Color(0xFF2A2419),
    secondaryContainer = Color(0xFF3A3226),
    onSecondaryContainer = Color(0xFFEDE7DA),

    tertiary = Color(0xFF8FC7A8),
    onTertiary = Color(0xFF0B2015),
    tertiaryContainer = Color(0x338FC7A8),
    onTertiaryContainer = Color(0xFFD6EFE0),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0x33FFB4AB),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF8A8578),
    outlineVariant = Color(0xFF3F4642),

    inverseSurface = Color(0xFFF3EFE6),
    inverseOnSurface = Color(0xFF1F2421),
    inversePrimary = Color(0xFF2F6F4E),
    scrim = Color(0xCC000000),
)

/**
 * 扩展语义色（M3 ColorScheme 没有对应角色的实色，统一收口于此，禁止页面写死）
 */
object AppSemantic {
    // 已成稿（原 statusColor done #2E7D32）
    val success = Color(0xFF2E7D32)
    val onSuccess = Color(0xFFFFFFFF)
    val successContainer = Color(0x1A2E7D32)

    // 排队中/出稿中（原 #9B6B2F）
    val warning = Color(0xFF9B6B2F)
    val onWarning = Color(0xFFFFFFFF)
    val warningContainer = Color(0x1A9B6B2F)

    // 实心危险按钮（重试/放弃录音）
    val dangerButton = Color(0xFFB3261E)
    val onDangerButton = Color(0xFFFFFFFF)

    // 提示条（Snackbar）底/字
    val toastBackground = Color(0xE61F2421)
    val toastForeground = Color(0xFFFFFFFF)

    // 文字档位补充（M3 只有 on* 两档）
    val textHigh = Color(0xE61F2421)
    val textMedium = Color(0x991F2421)
    val textTertiary = Color(0x801F2421)
    val textDisabled = Color(0x4D1F2421)

    // ── 随主题派生的分层语义 ──

    /** 1dp 发丝分割线 */
    fun divider(scheme: ColorScheme): Color = scheme.onSurface.copy(alpha = 0.06f)
    val divider: Color
        @Composable @ReadOnlyComposable get() = divider(MaterialTheme.colorScheme)

    /** 设置页/详情页卡片底 */
    fun cardSurface(scheme: ColorScheme): Color = scheme.onSurface.copy(alpha = 0.03f)
    val cardSurface: Color
        @Composable @ReadOnlyComposable get() = cardSurface(MaterialTheme.colorScheme)

    /** 卡片描边 */
    fun cardStroke(scheme: ColorScheme): Color = scheme.onSurface.copy(alpha = 0.06f)
    val cardStroke: Color
        @Composable @ReadOnlyComposable get() = cardStroke(MaterialTheme.colorScheme)

    /** 录音中的呼吸圈底 */
    fun recordingHalo(scheme: ColorScheme): Color = scheme.primary.copy(alpha = 0.12f)
    val recordingHalo: Color
        @Composable @ReadOnlyComposable get() = recordingHalo(MaterialTheme.colorScheme)
}

/**
 * 装饰/品牌固定色（豁免类：永远不随主题变化的颜色）
 *
 * 由 IdeaCaptureTheme 经 CompositionLocal 下发，子树/预览/测试可覆盖；
 * 页面一律 AppDecor.current.xxx 访问，禁止 object 常量直连。
 */
data class AppDecorTokens(
    /** 设备/用户头像兜底色板：按标识哈希取色、同名恒同色，属装饰数据色不随主题 */
    val avatarPalette: List<Color> = listOf(
        Color(0xFF2F6F4E),
        Color(0xFF7A6A52),
        Color(0xFF4175C2),
        Color(0xFF9A6BD5),
        Color(0xFFC23B66),
        Color(0xFF49D9E2),
    ),
    /** 登录页顶部光晕（品牌固定色） */
    val loginGlowCore: Color = Color(0x2E2F6F4E),
    val loginGlowFar: Color = Color(0x142F6F4E),
)

val LocalAppDecor = staticCompositionLocalOf { AppDecorTokens() }

/** 页面访问入口：AppDecor.current.loginGlowCore —— 禁止 object 常量直连 */
object AppDecor {
    val current: AppDecorTokens
        @Composable @ReadOnlyComposable get() = LocalAppDecor.current
}
