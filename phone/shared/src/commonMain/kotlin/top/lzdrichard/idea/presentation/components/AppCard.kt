package top.lzdrichard.idea.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.lzdrichard.idea.presentation.theme.AppSemantic
import top.lzdrichard.idea.presentation.theme.AppSpacing
import top.lzdrichard.idea.presentation.theme.listCard

/**
 * 通用卡片（主题圆角 + 主题间距，禁止页面各写一份）
 *
 * 可点击时用 indication = null，符合「按钮/卡片无水波纹」的 UI 规范。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.listCard,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = AppSpacing.current
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(clickModifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppSemantic.cardStroke),
    ) {
        Column(modifier = Modifier.padding(spacing.lg), content = content)
    }
}
