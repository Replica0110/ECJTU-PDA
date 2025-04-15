package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.service.ExperimentData
import com.lonx.ecjtu.pda.service.StuExperimentService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StuExperimentUiState(
    val isLoading: Boolean = false,
    val experiments: List<ExperimentData>? = null,
    val error: String? = null // 用于显示错误消息
): BaseUiState

class StuExperimentViewModel(
    override val service: StuExperimentService,
    override val prefs: PreferencesManager
): BaseViewModel, ViewModel() {
    private val _uiState = MutableStateFlow(StuExperimentUiState())
    override val uiState: StateFlow<StuExperimentUiState> = _uiState.asStateFlow()

    fun loadExperiments() {
        viewModelScope.launch {
            _uiState.value = StuExperimentUiState(isLoading = true, experiments = null,error = null)
            when (val result = service.getExperiments()) {
                is ServiceResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, experiments = result.data) }
                }
                is ServiceResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
    fun retryLoadExperiments() {
        _uiState.update { it.copy(isLoading = true, experiments = null, error = null) }
        loadExperiments()
    }
}