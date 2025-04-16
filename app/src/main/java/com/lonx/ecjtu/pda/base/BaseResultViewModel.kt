package com.lonx.ecjtu.pda.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.common.toUiState
import com.lonx.ecjtu.pda.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseResultViewModel<T>(
    private val fetchData: suspend () -> ServiceResult<T>
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<T>>(UiState.Empty)
    val uiState: StateFlow<UiState<T>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_uiState.value is UiState.Loading) return // 避免重复请求

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = fetchData().toUiState()
        }
    }

    fun retry() {
        load()
    }
}
