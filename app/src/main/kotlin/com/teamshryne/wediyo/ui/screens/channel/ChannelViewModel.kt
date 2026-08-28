package com.teamshryne.wediyo.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.model.UiChannelHome
import com.teamshryne.wediyo.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChannelUiState(
    val browseId: String = "",
    val home: UiChannelHome? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChannelViewModel : ViewModel() {
    private val repo = ChannelRepository()
    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state

    fun load(browseId: String) {
        if (browseId.isBlank()) return
        _state.value = _state.value.copy(browseId = browseId, isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val h = repo.fetchHome(browseId)
                _state.value = _state.value.copy(home = h, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load channel")
            }
        }
    }

    fun retry() {
        val id = _state.value.browseId
        if (id.isNotBlank()) load(id)
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
