package top.lzdrichard.idea.presentation.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import top.lzdrichard.idea.presentation.components.AppButton
import top.lzdrichard.idea.presentation.components.AppCard
import top.lzdrichard.idea.presentation.components.AppTextButton
import top.lzdrichard.idea.presentation.components.StatusBadge
import top.lzdrichard.idea.presentation.feature.settings.SettingsDialog
import top.lzdrichard.idea.presentation.permission.rememberRecordPermission
import top.lzdrichard.idea.presentation.theme.AppSpacing

/** 首页：一个大按钮录音，下面是最新的稿子。 */
@Composable
fun HomeScreen(onOpenDetail: (String) -> Unit, onLoggedOut: () -> Unit) {
    val vm = koinViewModel<HomeViewModel>()
    val state by vm.state.collectAsState()
    val spacing = AppSpacing.current
    val snackbar = remember { SnackbarHostState() }
    val permission = rememberRecordPermission()
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.consumeMessage()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("随记", style = MaterialTheme.typography.titleLarge)
                    if (state.displayName.isNotBlank()) {
                        Text(
                            state.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AppTextButton(text = "设置", onClick = { showSettings = true })
            }

            Spacer(Modifier.height(spacing.lg))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppButton(
                    text = if (state.recording) "停止" else "录音",
                    onClick = {
                        when {
                            state.recording -> vm.stopRecording(onOpenDetail)
                            permission.granted -> vm.startRecording()
                            else -> permission.request()
                        }
                    },
                    modifier = Modifier.size(184.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    container = if (state.recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (state.recording) {
                Text(
                    text = formatSeconds(state.recordSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
                )
            } else if (state.stage != Stage.Idle) {
                Text(
                    text = state.stage.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
                )
            }
            if (state.pendingCount > 0) {
                Text(
                    text = "排队中 " + state.pendingCount + " 条，联网后自动送出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
                )
            }

            Spacer(Modifier.height(spacing.lg))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近的稿子", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AppTextButton(text = "刷新", onClick = vm::refresh)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = spacing.lg),
            ) {
                items(state.records, key = { it.clientId }) { record ->
                    AppCard(onClick = { onOpenDetail(record.clientId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = record.templateName ?: "未分类",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            StatusBadge(record.status)
                        }
                        if (!record.preview.isNullOrBlank()) {
                            Text(
                                text = record.preview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onLoggedOut = {
                showSettings = false
                onLoggedOut()
            },
        )
    }
}

private fun formatSeconds(total: Int): String {
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes > 0) {
        minutes.toString() + " 分 " + seconds.toString().padStart(2, '0') + " 秒"
    } else {
        total.toString() + " 秒"
    }
}
