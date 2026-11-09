package top.lzdrichard.idea.presentation.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.koin.compose.viewmodel.koinViewModel
import top.lzdrichard.idea.presentation.components.AppLabeledField
import top.lzdrichard.idea.presentation.components.AppTextButton
import top.lzdrichard.idea.presentation.theme.AppSpacing

/**
 * 设置：服务端地址、转写地址、转写方式（自动 / 只用手机本地 / 只用在线）。
 * 地址与方式都落本地，改完立刻生效，不用重登。
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, onLoggedOut: () -> Unit) {
    val vm = koinViewModel<SettingsViewModel>()
    val state by vm.state.collectAsState()
    val spacing = AppSpacing.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AppLabeledField(
                    label = "服务端地址",
                    value = state.serverUrl,
                    onValueChange = vm::onServerUrlChange,
                )
                Spacer(Modifier.height(spacing.md))
                AppLabeledField(
                    label = "转写服务",
                    value = state.asrUrl,
                    onValueChange = vm::onAsrUrlChange,
                )
                Text(
                    "转写服务要填到 /v1",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.lg))
                Text("转写方式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(spacing.xs))
                RouteOption("auto", "自动（手机能转就手机转，不行走在线）", state.asrRoute, vm::onRouteChange)
                RouteOption("native", "只用手机本地转写", state.asrRoute, vm::onRouteChange)
                RouteOption("api", "只用在线转写", state.asrRoute, vm::onRouteChange)
            }
        },
        confirmButton = {
            AppTextButton(text = "保存", onClick = { vm.save(onDismiss) })
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                AppTextButton(
                    text = "退出登录",
                    onClick = { vm.logout(onLoggedOut) },
                    color = MaterialTheme.colorScheme.error,
                )
                AppTextButton(text = "关闭", onClick = onDismiss)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun RouteOption(
    value: String,
    label: String,
    current: String,
    onChange: (String) -> Unit,
) {
    val spacing = AppSpacing.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null) { onChange(value) }
            .padding(vertical = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = current == value,
            onClick = { onChange(value) },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
