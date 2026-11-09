package top.lzdrichard.idea.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 应用 Shapes 定义（结构照搬 LoostoneCenter）
 *
 * M3 Shapes 五槽承载主干档：
 * - extraSmall (8dp)  — 标签底、输入框内嵌块
 * - small (12dp)      — 次级容器
 * - medium (16dp)     — 卡片、按钮
 * - large (22dp)      — 大卡
 * - extraLarge (30dp) — 弹窗/大圆卡
 *
 * 溢出一档用 Shapes 扩展属性：Shapes.listCard (24dp)。
 * 胶囊/圆形直接用 CircleShape 或 RoundedCornerShape(50)，不在此定义。
 * 页面禁止再写 RoundedCornerShape(数字)，一律取 MaterialTheme.shapes。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/** 24dp「最近稿子」列表卡档 */
val Shapes.listCard: Shape get() = RoundedCornerShape(24.dp)

/** 主题模式（默认 Light：暖沙底；Dark 为结构预留） */
enum class AppThemeMode {
    DARK,
    LIGHT;

    companion object {
        fun fromName(name: String): AppThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: LIGHT
    }
}

@Composable
fun rememberAppThemeMode(initial: AppThemeMode = AppThemeMode.LIGHT): AppThemeMode {
    var mode by remember { mutableStateOf(initial) }
    return mode
}

/**
 * 应用主题 Composable —— 根布局使用，包裹整个应用内容。
 *
 * 提供 colorScheme + typography + shapes 三件套（MaterialTheme 内建通道），
 * 以及扩展 token：LocalAppSpacing / LocalAppDecor。
 */
@Composable
fun IdeaCaptureTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacingTokens(),
        LocalAppDecor provides AppDecorTokens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
