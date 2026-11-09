package top.lzdrichard.idea.presentation.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lzdrichard.idea.core.AppLog
import top.lzdrichard.idea.data.AuthRepository
import top.lzdrichard.idea.data.NetworkException
import top.lzdrichard.idea.data.PendingQueue
import top.lzdrichard.idea.data.RecordItem
import top.lzdrichard.idea.data.RecordRepository
import top.lzdrichard.idea.data.RecordingController

/** 首页所处的处理阶段，界面据此显示「正在转写 / 正在提交 / 正在出稿」。 */
enum class Stage(val label: String) {
    Idle(""),
    Transcribing("正在转写"),
    Submitting("正在提交"),
    Waiting("正在出稿"),
}

data class HomeUiState(
    val displayName: String = "",
    val records: List<RecordItem> = emptyList(),
    val pendingCount: Int = 0,
    val recording: Boolean = false,
    val recordSeconds: Int = 0,
    val stage: Stage = Stage.Idle,
    val message: String? = null,
)

class HomeViewModel(
    private val auth: AuthRepository,
    private val records: RecordRepository,
    private val recording: RecordingController,
    private val pending: PendingQueue,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(displayName = auth.displayName, pendingCount = pending.count()),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var secondsJob: Job? = null
    private var flushing = false

    init {
        refresh()
        // 排队条目的后台补送：每 20 秒试一次，网络恢复后自动送出
        viewModelScope.launch {
            while (true) {
                delay(20_000)
                if (pending.count() > 0) flushPending()
            }
        }
        if (pending.count() > 0) flushPending()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val fresh = records.list()
                _state.update { state -> state.copy(records = fresh) }
            } catch (e: NetworkException) {
                _state.update { it.copy(message = e.message ?: "连不上服务端") }
            } catch (e: Exception) {
                AppLog.w("HomeViewModel", "拉列表失败：" + e.message)
            }
        }
    }

    fun startRecording() {
        if (_state.value.recording) return
        if (!recording.start()) {
            _state.update { it.copy(message = "录音启动失败，检查录音权限") }
            return
        }
        _state.update { it.copy(recording = true, recordSeconds = 0, stage = Stage.Idle) }
        secondsJob?.cancel()
        secondsJob = viewModelScope.launch {
            while (_state.value.recording) {
                delay(1_000)
                if (_state.value.recording) _state.update { it.copy(recordSeconds = it.recordSeconds + 1) }
            }
        }
    }

    fun cancelRecording() {
        recording.cancel()
        secondsJob?.cancel()
        _state.update { it.copy(recording = false, recordSeconds = 0, stage = Stage.Idle) }
    }

    /** 停止录音 → 转写 → 提交；出稿进度交给详情页轮询。 */
    fun stopRecording(onSubmitted: (String) -> Unit) {
        _state.update { it.copy(recording = false) }
        secondsJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(stage = Stage.Transcribing) }
            try {
                val outcome = recording.stop()
                _state.update { it.copy(stage = Stage.Idle) }
                when {
                    outcome.clientId != null && !outcome.queued -> {
                        _state.update { it.copy(pendingCount = pending.count()) }
                        onSubmitted(outcome.clientId)
                        refresh()
                    }
                    outcome.queued -> {
                        _state.update {
                            it.copy(pendingCount = pending.count(), message = outcome.message)
                        }
                    }
                    else -> _state.update { it.copy(message = outcome.message ?: "这条没成，重录一次") }
                }
            } catch (e: Exception) {
                AppLog.e("HomeViewModel", "停录处理失败：" + e.message)
                _state.update { it.copy(stage = Stage.Idle, message = e.message ?: "处理失败") }
            }
        }
    }

    fun flushPending() {
        if (flushing) return
        flushing = true
        viewModelScope.launch {
            try {
                val result = pending.flush()
                _state.update { state ->
                    state.copy(
                        pendingCount = result.remaining,
                        message = if (result.sent > 0 && result.remaining == 0) "排队的稿子都送出去了" else state.message,
                    )
                }
                if (result.sent > 0) refresh()
            } finally {
                flushing = false
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
