package top.lzdrichard.idea.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.lzdrichard.idea.presentation.theme.AppSpacing

/** 加载态：转圈 + 一句说明（正在转写/正在出稿/正在取稿）。 */
@Composable
fun LoadingBlock(text: String, modifier: Modifier = Modifier) {
    val spacing = AppSpacing.current
    Column(
        modifier = modifier.fillMaxWidth().padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.md),
        )
    }
}
