package top.lzdrichard.idea.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF2F6F4E)
private val Sand = Color(0xFFF6F2EA)
private val Ink = Color(0xFF1F2421)

@Composable
fun IdeaApp(state: AppState) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            state.message = null
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            surface = Sand,
            background = Sand,
            onSurface = Ink,
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Sand) {
            Box(Modifier.fillMaxSize()) {
                when (val screen = state.screen) {
                    Screen.Login -> LoginScreen(state)
                    Screen.Templates -> TemplateScreen(state)
                    Screen.Home -> HomeScreen(state)
                    is Screen.Detail -> DetailScreen(state, screen.clientId)
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun LoginScreen(state: AppState) {
    var server by remember { mutableStateOf(state.serverUrl) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("随记", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Green)
        Spacer(Modifier.height(6.dp))
        Text("说一段话，回来是稿子", color = Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = server,
            onValueChange = {
                server = it
                state.updateServerUrl(it)
            },
            label = { Text("服务端地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { state.login(user, pass) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
            else Text("登录")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateScreen(state: AppState) {
    val selectable = state.templates.filter { !it.isBuiltin }
    val builtin = state.templates.filter { it.isBuiltin }
    var picked by remember(state.templates) {
        mutableStateOf(selectable.filter { it.selected }.map { it.id }.toSet())
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("挑几种你常用的") }) },
        containerColor = Sand,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("以后每段录音只在这些体裁里判，通用兜底自动带上。", color = Ink.copy(alpha = 0.65f))
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(selectable) { template ->
                    TemplateCard(template.name, template.criterion, picked.contains(template.id)) {
                        picked = if (picked.contains(template.id)) picked - template.id else picked + template.id
                    }
                }
                items(builtin) { template ->
                    TemplateCard(template.name, "固定勾选，判不准时用它", true, enabled = false) {}
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { state.saveTemplates(picked.toList()) },
                enabled = picked.isNotEmpty() && !state.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("开始用") }
        }
    }
}

@Composable
private fun TemplateCard(name: String, criterion: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onToggle() },
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { if (enabled) onToggle() }, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(criterion, fontSize = 13.sp, color = Ink.copy(alpha = 0.6f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(state: AppState) {
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.startRecording() else state.message = "没有录音权限"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.displayName.isBlank()) "随记" else state.displayName) },
                actions = { TextButton(onClick = { showSettings = true }) { Text("设置") } },
            )
        },
        containerColor = Sand,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                if (state.recording) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatSeconds(state.recordSeconds), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Green)
                        Spacer(Modifier.height(10.dp))
                        Text("正在录音，点一下停", color = Ink.copy(alpha = 0.6f))
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { state.stopRecording() },
                            modifier = Modifier.size(96.dp),
                            shape = CircleShape,
                        ) { Text("停止") }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { state.cancelRecording() }) { Text("不要了") }
                    }
                } else if (state.stage != Stage.Idle) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Green)
                        Spacer(Modifier.height(12.dp))
                        Text(state.stage.label, color = Ink.copy(alpha = 0.7f))
                    }
                } else {
                    Button(
                        onClick = {
                            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (granted) state.startRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.size(104.dp),
                        shape = CircleShape,
                    ) { Text("录音", fontSize = 18.sp) }
                }
            }

            if (state.pendingCount > 0) {
                Text(
                    "排队中 " + state.pendingCount + " 条，联网后自动送出",
                    fontSize = 12.sp,
                    color = Color(0xFF9B6B2F),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("最近的稿子", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.refreshRecords() }) { Text("刷新") }
            }

            if (state.records.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("还没有稿子", color = Ink.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.records) { item ->
                        Card(
                            Modifier.fillMaxWidth().clickable { state.openRecord(item.clientId) },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.templateName ?: statusLabel(item.status), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(statusLabel(item.status), fontSize = 12.sp, color = statusColor(item.status))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.preview.ifBlank { "（还在处理）" }, fontSize = 14.sp, color = Ink.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(state) { showSettings = false }
    }
}

@Composable
private fun SettingsDialog(state: AppState, onClose: () -> Unit) {
    var server by remember { mutableStateOf(state.serverUrl) }
    var asr by remember { mutableStateOf(state.asrUrl) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("设置") },
        text = {
            Column {
                OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("服务端") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = asr, onValueChange = { asr = it }, label = { Text("转写服务") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("转写服务要填到 /v1", fontSize = 12.sp, color = Ink.copy(alpha = 0.55f))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.updateServerUrl(server.trim())
                state.updateAsrUrl(asr.trim())
                onClose()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = {
                state.logout()
                onClose()
            }) { Text("退出登录") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(state: AppState, clientId: String) {
    val detail = state.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.document?.templateName ?: "稿子") },
                navigationIcon = { TextButton(onClick = { state.backHome() }) { Text("返回") } },
            )
        },
        containerColor = Sand,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            when {
                detail == null -> {
                    CircularProgressIndicator(color = Green)
                    Spacer(Modifier.height(8.dp))
                    Text("正在取稿…", color = Ink.copy(alpha = 0.6f))
                }
                detail.status == "failed" -> {
                    Text("出稿失败", fontWeight = FontWeight.Bold, color = Color(0xFFB3261E))
                    Spacer(Modifier.height(8.dp))
                    Text(detail.error ?: "未知原因", color = Ink.copy(alpha = 0.7f))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { state.retryCurrent() }) { Text("重试") }
                    Spacer(Modifier.height(20.dp))
                    Text("原始转写", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(detail.rawText, fontSize = 14.sp, color = Ink.copy(alpha = 0.7f))
                }
                detail.document == null -> {
                    CircularProgressIndicator(color = Green)
                    Spacer(Modifier.height(8.dp))
                    Text("正在出稿，通常十几秒", color = Ink.copy(alpha = 0.6f))
                }
                else -> {
                    if (!detail.document.genreReason.isNullOrBlank()) {
                        Text(detail.document.genreReason, fontSize = 13.sp, color = Ink.copy(alpha = 0.55f))
                        Spacer(Modifier.height(12.dp))
                    }
                    MarkdownView(detail.document.bodyMd)
                }
            }
        }
    }
}

private fun formatSeconds(total: Int): String {
    val m = total / 60
    val s = total % 60
    return String.format("%02d:%02d", m, s)
}

fun statusLabel(status: String): String = when (status) {
    "done" -> "已成稿"
    "failed" -> "失败"
    "running" -> "出稿中"
    else -> "排队中"
}

fun statusColor(status: String): Color = when (status) {
    "done" -> Color(0xFF2E7D32)
    "failed" -> Color(0xFFB3261E)
    else -> Color(0xFF8A6D3B)
}

@Composable
fun MarkdownView(md: String) {
    val blocks = remember(md) { parseMarkdown(md) }
    Column(Modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    Spacer(Modifier.height(if (block.level == 1) 18.dp else 14.dp))
                    Text(
                        block.text,
                        fontSize = if (block.level == 1) 22.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                is MdBlock.Bullet -> {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("·", color = Green, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(inlineStyled(block.text), fontSize = 15.sp, color = Ink, lineHeight = 23.sp)
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(inlineStyled(block.text), fontSize = 15.sp, color = Ink, lineHeight = 23.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
                is MdBlock.Code -> {
                    Spacer(Modifier.height(8.dp))
                    if (block.lang == "mermaid") {
                        Surface(
                            color = Color(0xFFEDE7DA),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                MermaidDiagram(block.text)
                            }
                        }
                    } else {
                        Surface(color = Color(0xFFEDE7DA), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(block.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Ink)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val lang: String, val text: String) : MdBlock
}

private fun parseMarkdown(md: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = md.replace("\r\n", "\n").split("\n")
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()
        when {
            line.startsWith("```") -> {
                val lang = line.removePrefix("```").trim()
                val content = StringBuilder()
                i += 1
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    content.appendLine(lines[i])
                    i += 1
                }
                blocks.add(MdBlock.Code(lang, content.toString().trimEnd()))
            }
            line.startsWith("## ") -> blocks.add(MdBlock.Heading(2, line.removePrefix("## ").trim()))
            line.startsWith("# ") -> blocks.add(MdBlock.Heading(1, line.removePrefix("# ").trim()))
            line.startsWith("- ") || line.startsWith("* ") -> blocks.add(MdBlock.Bullet(line.substring(2).trim()))
            line.isEmpty() -> Unit
            else -> blocks.add(MdBlock.Paragraph(line))
        }
        i += 1
    }
    return blocks
}

@Composable
private fun inlineStyled(text: String): AnnotatedString {
    return remember(text) {
        buildAnnotatedString {
            var index = 0
            while (index < text.length) {
                val start = text.indexOf("**", index)
                if (start < 0) {
                    append(text.substring(index))
                    break
                }
                val end = text.indexOf("**", start + 2)
                if (end < 0) {
                    append(text.substring(index))
                    break
                }
                append(text.substring(index, start))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(start + 2, end))
                }
                index = end + 2
            }
        }
    }
}
