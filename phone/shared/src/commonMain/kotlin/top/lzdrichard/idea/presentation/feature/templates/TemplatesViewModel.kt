package top.lzdrichard.idea.presentation.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.lzdrichard.idea.data.Api
import top.lzdrichard.idea.data.ConfigRepository
import top.lzdrichard.idea.data.Template

/** 首登选体裁页状态。 */
data class TemplatesUiState(
    val selectable: List<Template> = emptyList(),
    val builtin: List<Template> = emptyList(),
    val picked: Set<Int> = emptySet(),
    val busy: Boolean = false,
    val error: String? = null,
)

class TemplatesViewModel(
    private val config: ConfigRepository,
    private val api: Api,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val templates = config.config.value?.templates ?: runCatching { config.refresh() }.getOrNull()?.templates
        if (templates == null) {
            _state.update { it.copy(error = "拉不到体裁列表") }
            return
        }
        _state.update {
            it.copy(
                selectable = templates.filter { template -> !template.isBuiltin },
                builtin = templates.filter { template -> template.isBuiltin },
                picked = templates.filter { template -> template.selected && !template.isBuiltin }
                    .map { template -> template.id }
                    .toSet(),
            )
        }
    }

    fun toggle(id: Int) = _state.update {
        val next = if (it.picked.contains(id)) it.picked - id else it.picked + id
        it.copy(picked = next, error = null)
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.picked.isEmpty() || current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                api.saveTemplates(current.picked.toList())
                config.refresh()
                _state.update { it.copy(busy = false) }
                onSaved()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "保存失败") }
            }
        }
    }
}
