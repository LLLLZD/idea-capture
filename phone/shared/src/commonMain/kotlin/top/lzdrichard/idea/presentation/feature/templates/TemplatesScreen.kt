package top.lzdrichard.idea.presentation.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import top.lzdrichard.idea.data.Template
import top.lzdrichard.idea.presentation.components.AppButton
import top.lzdrichard.idea.presentation.components.AppCard
import top.lzdrichard.idea.presentation.theme.AppSpacing

/**
 * 首登/改体裁：至少勾一种；「通用」是兜底模板，展示但不可取消。
 */
@Composable
fun TemplatesScreen(onSaved: () -> Unit) {
    val vm = koinViewModel<TemplatesViewModel>()
    val state by vm.state.collectAsState()
    val spacing = AppSpacing.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.lg),
        ) {
            Spacer(Modifier.height(spacing.lg))
            Text("选几种常写的", style = MaterialTheme.typography.titleLarge)
            Text(
                "只在这几种里判断体裁；通用兜底始终可用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xxs),
            )
            Spacer(Modifier.height(spacing.lg))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(state.selectable, key = { it.id }) { template ->
                    TemplateRow(
                        template = template,
                        checked = state.picked.contains(template.id),
                        onToggle = { vm.toggle(template.id) },
                    )
                }
                items(state.builtin, key = { "builtin-" + it.id }) { template ->
                    TemplateRow(template = template, checked = true, onToggle = {})
                }
            }
            AppButton(
                text = if (state.picked.isEmpty()) "至少选一种" else "开始用",
                onClick = { vm.save(onSaved) },
                loading = state.busy,
                enabled = state.picked.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
            )
        }
    }
}

@Composable
private fun TemplateRow(template: Template, checked: Boolean, onToggle: () -> Unit) {
    val spacing = AppSpacing.current
    AppCard(onClick = if (template.isBuiltin) null else onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleSmall)
                if (template.criterion.isNotBlank()) {
                    Text(
                        template.criterion,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            Checkbox(
                checked = checked,
                onCheckedChange = { if (!template.isBuiltin) onToggle() },
                enabled = !template.isBuiltin,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}
