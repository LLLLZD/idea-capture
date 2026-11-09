package top.lzdrichard.idea.presentation.feature.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import top.lzdrichard.idea.presentation.components.AppButton
import top.lzdrichard.idea.presentation.components.AppTextButton
import top.lzdrichard.idea.presentation.components.LoadingBlock
import top.lzdrichard.idea.presentation.components.MarkdownView
import top.lzdrichard.idea.presentation.components.StatusBadge
import top.lzdrichard.idea.presentation.theme.AppSpacing

/** 稿子详情：出稿中转圈轮询，完成渲染 Markdown（含流程图），失败可重试。 */
@Composable
fun DetailScreen(clientId: String, onBack: () -> Unit) {
    val vm = koinViewModel<DetailViewModel>()
    val state by vm.state.collectAsState()
    val spacing = AppSpacing.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(clientId) { vm.open(clientId) }
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.consumeError()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md, bottom = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(text = "返回", onClick = onBack)
                Spacer(Modifier.weight(1f))
                state.detail?.let { detail ->
                    Text(
                        text = detail.document?.templateName ?: "未分类",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                    StatusBadge(detail.status)
                }
            }
            val detail = state.detail
            when {
                state.loading && detail == null -> LoadingBlock("正在取稿", Modifier.fillMaxSize())
                detail == null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "拉不到这篇稿子",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(spacing.md))
                    AppButton(text = "重试", onClick = { vm.open(clientId) })
                }
                detail.status == "failed" -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "出稿失败：" + (detail.error ?: "未知原因"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(spacing.md))
                    AppButton(text = "重新出稿", onClick = vm::retry)
                }
                detail.status != "done" -> LoadingBlock("正在出稿", Modifier.fillMaxSize())
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = spacing.xxl),
                ) {
                    MarkdownView(detail.document?.bodyMd ?: "")
                }
            }
        }
    }
}
