package top.lzdrichard.idea.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.lzdrichard.idea.presentation.theme.AppSemantic
import top.lzdrichard.idea.presentation.theme.AppSpacing

/** 稿子状态标签：已成稿 / 出稿中 / 失败 / 排队中。 */
@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val spacing = AppSpacing.current
    val label: String
    val color: Color
    when (status) {
        "done" -> {
            label = "已成稿"
            color = AppSemantic.success
        }
        "failed" -> {
            label = "失败"
            color = MaterialTheme.colorScheme.error
        }
        "running" -> {
            label = "出稿中"
            color = AppSemantic.warning
        }
        else -> {
            label = "排队中"
            color = AppSemantic.warning
        }
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
        )
    }
}
