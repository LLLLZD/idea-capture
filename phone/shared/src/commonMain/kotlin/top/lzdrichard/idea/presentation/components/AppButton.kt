package top.lzdrichard.idea.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.lzdrichard.idea.presentation.theme.AppSpacing

/**
 * 主按钮（UI 规范：点击不允许有水波纹）
 *
 * 用 Surface + clickable(indication = null) 自己画，而不是 Material3 Button——
 * 后者带 ripple 且圆角/高度被内建约束，不符合设计稿。
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val spacing = AppSpacing.current
    val interaction = remember { MutableInteractionSource() }
    val clickable = enabled && !loading
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = clickable,
                onClick = onClick,
            ),
        shape = shape,
        color = if (clickable) container else container.copy(alpha = 0.38f),
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.heightIn(min = 20.dp, max = 20.dp),
                    color = contentColor,
                )
            } else {
                Text(text = text, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** 文字按钮（弱操作：返回、刷新、取消）——同样无 ripple。 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val spacing = AppSpacing.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) color else color.copy(alpha = 0.38f),
        )
    }
}
