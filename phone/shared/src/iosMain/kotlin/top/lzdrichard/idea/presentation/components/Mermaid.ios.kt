package top.lzdrichard.idea.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import top.lzdrichard.idea.presentation.theme.AppSpacing

/** iOS 端暂不挂 mermaid 渲染器：源码等宽展示，保证稿子主体可读。 */
@Composable
actual fun MermaidDiagram(code: String, modifier: Modifier) {
    Text(
        text = code,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth().padding(AppSpacing.current.md),
    )
}
