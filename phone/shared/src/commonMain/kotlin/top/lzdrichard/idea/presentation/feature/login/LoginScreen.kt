package top.lzdrichard.idea.presentation.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.input.KeyboardType
import org.koin.compose.viewmodel.koinViewModel
import top.lzdrichard.idea.presentation.components.AppButton
import top.lzdrichard.idea.presentation.components.AppTextField
import top.lzdrichard.idea.presentation.theme.AppSpacing

/** 登录页：服务端地址 + 账号密码，默认填线上地址。 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val vm = koinViewModel<LoginViewModel>()
    val state by vm.state.collectAsState()
    val spacing = AppSpacing.current
    val snackbar = remember { SnackbarHostState() }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(spacing.xxl))
            Text("随记", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "说一段，拿一篇稿",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs),
            )
            Spacer(Modifier.height(spacing.xxl))
            AppTextField(
                value = state.serverUrl,
                onValueChange = vm::onServerUrlChange,
                label = "服务端地址",
                keyboardType = KeyboardType.Uri,
            )
            Spacer(Modifier.height(spacing.md))
            AppTextField(
                value = state.username,
                onValueChange = vm::onUsernameChange,
                label = "用户名",
            )
            Spacer(Modifier.height(spacing.md))
            AppTextField(
                value = state.password,
                onValueChange = vm::onPasswordChange,
                label = "密码",
                password = true,
            )
            Spacer(Modifier.height(spacing.xl))
            AppButton(
                text = "登录",
                onClick = { vm.login(onLoggedIn) },
                loading = state.busy,
                enabled = state.username.isNotBlank() && state.password.isNotBlank(),
                modifier = Modifier.fillMaxSize(),
            )
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}
