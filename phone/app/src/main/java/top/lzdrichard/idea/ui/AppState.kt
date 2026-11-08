package top.lzdrichard.idea.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.lzdrichard.idea.audio.Asr
import top.lzdrichard.idea.audio.Recorder
import top.lzdrichard.idea.data.Api
import top.lzdrichard.idea.data.ApiException
import top.lzdrichard.idea.data.ClientConfig
import top.lzdrichard.idea.data.PendingSubmit
import top.lzdrichard.idea.data.Prefs
import top.lzdrichard.idea.data.RecordDetail
import top.lzdrichard.idea.data.RecordItem
import top.lzdrichard.idea.data.Template

sealed interface Screen {
    data object Login : Screen
    data object Templates : Screen
    data object Home : Screen
    data class Detail(val clientId: String) : Screen
}

enum class Stage(val label: String) {
    Idle(""),
    Transcribing("正在转写"),
    Submitting("正在提交"),
    Waiting("正在出稿"),
}

class AppState(private val context: Context) {

    val prefs = Prefs(context)
    val recorder = Recorder(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var screen by mutableStateOf<Screen>(Screen.Login)
    var serverUrl by mutableStateOf(prefs.serverUrl)
    var asrUrl by mutableStateOf(prefs.asrUrl)
    var displayName by mutableStateOf(prefs.displayName)
    var config by mutableStateOf<ClientConfig?>(null)
    var templates by mutableStateOf<List<Template>>(emptyList())
    var records by mutableStateOf<List<RecordItem>>(emptyList())
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    var recording by mutableStateOf(false)
    var recordSeconds by mutableStateOf(0)
    var stage by mutableStateOf(Stage.Idle)

    var detail by mutableStateOf<RecordDetail?>(null)
    var detailId by mutableStateOf<String?>(null)

    var pendingCount by mutableStateOf(PendingSubmit.list(prefs.pending).size)
    private var flushing = false

    init {
        Api.baseUrl = prefs.serverUrl
        Api.token = prefs.token
        if (prefs.token != null) {
            scope.launch {
                bootstrap()
                flushPending()
            }
        }
        // 后台每 20 秒试一次，把排队的提交补送出去
        scope.launch {
            while (true) {
                delay(20_000)
                if (pendingCount > 0 && Api.token != null) flushPending()
            }
        }
    }

    private suspend fun bootstrap() {
        busy = true
        try {
            val cfg = Api.clientConfig()
            config = cfg
            templates = cfg.templates
            if (cfg.asrPresets.isNotEmpty() && prefs.asrUrl == Prefs.DEFAULT_ASR) {
                asrUrl = cfg.asrPresets.first().url
                prefs.asrUrl = asrUrl
            }
            if (cfg.templates.none { it.selected && !it.isBuiltin }) {
                screen = Screen.Templates
            } else {
                screen = Screen.Home
                refreshRecords()
            }
        } catch (e: Exception) {
            message = e.message ?: "连不上服务端"
            prefs.clearLogin()
            Api.token = null
            screen = Screen.Login
        } finally {
            busy = false
        }
    }

    fun login(username: String, password: String) {
        val url = serverUrl.trim()
        if (url.isBlank()) {
            message = "先填服务端地址"
            return
        }
        Api.baseUrl = url
        prefs.serverUrl = url
        scope.launch {
            busy = true
            try {
                val result = Api.login(username.trim(), password)
                Api.token = result.token
                prefs.token = result.token
                displayName = result.displayName.ifBlank { username.trim() }
                prefs.displayName = displayName
                val cfg = Api.clientConfig()
                config = cfg
                templates = cfg.templates
                if (cfg.asrPresets.isNotEmpty() && prefs.asrUrl == Prefs.DEFAULT_ASR) {
                    asrUrl = cfg.asrPresets.first().url
                    prefs.asrUrl = asrUrl
                }
                screen = if (result.needsTemplateSetup) Screen.Templates else Screen.Home
                if (screen == Screen.Home) refreshRecords()
            } catch (e: Exception) {
                message = e.message ?: "登录失败"
            } finally {
                busy = false
            }
        }
    }

    fun saveTemplates(ids: List<Int>) {
        scope.launch {
            busy = true
            try {
                Api.saveTemplates(ids)
                screen = Screen.Home
                refreshRecords()
            } catch (e: Exception) {
                message = e.message ?: "保存失败"
            } finally {
                busy = false
            }
        }
    }

    fun refreshRecords() {
        scope.launch {
            try {
                records = Api.records()
            } catch (e: Exception) {
                message = e.message ?: "拉列表失败"
            }
        }
    }

    fun startRecording() {
        if (!recorder.start()) {
            message = "录音启动失败，检查录音权限"
            return
        }
        recording = true
        recordSeconds = 0
        scope.launch {
            while (recording) {
                delay(1000)
                if (recording) recordSeconds += 1
            }
        }
    }

    fun cancelRecording() {
        recording = false
        recorder.cancel()
        stage = Stage.Idle
    }

    fun stopRecording() {
        val result = recorder.stop()
        recording = false
        if (result == null) {
            message = "录音太短，没存下来"
            return
        }
        val (file, duration) = result
        val clientId = UUID.randomUUID().toString()
        scope.launch {
            try {
                stage = Stage.Transcribing
                val text = try {
                    Asr.transcribe(file, asrUrl, asrKeyFor(asrUrl))
                } catch (e: IOException) {
                    // 转写就要联网；断网时先留住录音，联网后再补转写，别把用户说的话丢了
                    val kept = keepAudio(file, clientId)
                    enqueuePending(clientId, "", duration, kept)
                    stage = Stage.Idle
                    message = "网络不通，录音已存本机，联网后自动补转写"
                    return@launch
                }
                file.delete()
                if (text.isBlank()) {
                    stage = Stage.Idle
                    message = "没转出文字"
                    return@launch
                }
                stage = Stage.Submitting
                try {
                    Api.submit(clientId, text, duration)
                    stage = Stage.Idle
                    openRecord(clientId)
                } catch (e: IOException) {
                    enqueuePending(clientId, text, duration)
                    stage = Stage.Idle
                    message = "网络不通，已排队，联网后自动送出"
                } catch (e: ApiException) {
                    if (e.statusCode == 429) {
                        enqueuePending(clientId, text, duration)
                        stage = Stage.Idle
                        message = "刚交过一条，这条先排队，稍后自动送出"
                    } else {
                        throw e
                    }
                }
            } catch (e: Exception) {
                stage = Stage.Idle
                message = e.message ?: "处理失败"
            }
        }
    }

    /** 把录音从缓存目录搬进私有目录，排队期间不会被系统清掉。 */
    private fun keepAudio(file: java.io.File, clientId: String): String? {
        return try {
            val dir = java.io.File(context.filesDir, "pending").apply { mkdirs() }
            val target = java.io.File(dir, clientId + ".m4a")
            file.copyTo(target, overwrite = true)
            file.delete()
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun enqueuePending(clientId: String, text: String, durationMs: Int, audioPath: String? = null) {
        val items = PendingSubmit.list(prefs.pending)
        if (items.none { it.clientId == clientId }) {
            items.add(PendingSubmit(clientId, text, durationMs, System.currentTimeMillis(), audioPath))
            prefs.pending = PendingSubmit.dump(items)
        }
        pendingCount = PendingSubmit.list(prefs.pending).size
    }

    /** 按顺序补送排队的提交；遇到网络错误就停下，等下一轮。没转写的先补转写。 */
    fun flushPending() {
        if (flushing) return
        val items = PendingSubmit.list(prefs.pending)
        if (items.isEmpty()) {
            pendingCount = 0
            return
        }
        flushing = true
        scope.launch {
            val remaining = mutableListOf<PendingSubmit>()
            var stop = false
            for (item in items) {
                if (stop) {
                    remaining.add(item)
                    continue
                }
                var text = item.text
                val audio = item.audioPath?.let { java.io.File(it) }
                if (text.isBlank()) {
                    if (audio == null || !audio.exists()) {
                        continue // 录音也没了，这条救不回来
                    }
                    try {
                        text = Asr.transcribe(audio, asrUrl, asrKeyFor(asrUrl))
                    } catch (e: IOException) {
                        stop = true
                        remaining.add(item)
                        continue
                    } catch (e: ApiException) {
                        audio.delete()
                        continue // 这条录音确实没内容，丢掉
                    } catch (e: Exception) {
                        stop = true
                        remaining.add(item)
                        continue
                    }
                    if (text.isBlank()) {
                        audio.delete()
                        continue
                    }
                }
                try {
                    Api.submit(item.clientId, text, item.durationMs)
                    audio?.delete()
                } catch (e: IOException) {
                    stop = true
                    remaining.add(item.copy(text = text))
                } catch (e: ApiException) {
                    if (e.statusCode == 429) {
                        stop = true
                        remaining.add(item.copy(text = text))
                    } else {
                        audio?.delete() // 其余 4xx 视为服务端不接受，直接丢弃
                    }
                } catch (e: Exception) {
                    stop = true
                    remaining.add(item.copy(text = text))
                }
            }
            prefs.pending = PendingSubmit.dump(remaining)
            pendingCount = remaining.size
            flushing = false
            if (remaining.isEmpty()) {
                message = "排队的稿子都送出去了"
                refreshRecords()
            }
        }
    }

    private fun asrKeyFor(url: String): String =
        config?.asrPresets?.firstOrNull { it.url.trimEnd('/') == url.trimEnd('/') }?.apiKey.orEmpty()

    fun openRecord(clientId: String) {
        detailId = clientId
        screen = Screen.Detail(clientId)
        detail = null
        scope.launch {
            var attempts = 0
            while (true) {
                try {
                    val current = Api.record(clientId)
                    detail = current
                    if (current.status == "done" || current.status == "failed") break
                } catch (e: Exception) {
                    message = e.message ?: "取稿失败"
                }
                attempts += 1
                if (attempts > 60) break
                delay(3000)
            }
        }
    }

    fun retryCurrent() {
        val clientId = detailId ?: return
        scope.launch {
            try {
                Api.retry(clientId)
                openRecord(clientId)
            } catch (e: Exception) {
                message = e.message ?: "重试失败"
            }
        }
    }

    fun backHome() {
        screen = Screen.Home
        detail = null
        detailId = null
        refreshRecords()
    }

    fun updateAsrUrl(url: String) {
        asrUrl = url
        prefs.asrUrl = url
    }

    fun updateServerUrl(url: String) {
        serverUrl = url
        prefs.serverUrl = url
        Api.baseUrl = url
    }

    fun logout() {
        prefs.clearLogin()
        Api.token = null
        screen = Screen.Login
        detail = null
        records = emptyList()
    }
}
