package top.lzdrichard.idea.presentation.feature.detail

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
import top.lzdrichard.idea.data.RecordDetail
import top.lzdrichard.idea.data.RecordRepository

/** 稿子详情页状态。 */
data class DetailUiState(
    val clientId: String = "",
    val detail: RecordDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class DetailViewModel(
    private val records: RecordRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** 打开某条稿子；出稿中会每 3 秒拉一次，直到 done / failed。 */
    fun open(clientId: String) {
        pollJob?.cancel()
        _state.value = DetailUiState(clientId = clientId)
        pollJob = viewModelScope.launch {
            var attempts = 0
            while (true) {
                try {
                    val fresh = records.detail(clientId)
                    val terminal = fresh.status == "done" || fresh.status == "failed"
                    _state.update { it.copy(detail = fresh, loading = false, error = null) }
                    if (terminal) break
                } catch (e: Exception) {
                    _state.update { it.copy(loading = false, error = e.message ?: "取稿失败") }
                    AppLog.w("DetailViewModel", "取稿失败 client_id=" + clientId + "：" + e.message)
                }
                attempts += 1
                if (attempts > 60) break
                delay(3_000)
            }
        }
    }

    fun retry() {
        val clientId = _state.value.clientId
        if (clientId.isBlank()) return
        viewModelScope.launch {
            try {
                records.retry(clientId)
                open(clientId)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "重试失败") }
            }
        }
    }

    fun reportRenderError(detail: String) {
        val clientId = _state.value.clientId
        if (clientId.isBlank()) return
        viewModelScope.launch { runCatching { records.reportRenderError(clientId, detail) } }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}
