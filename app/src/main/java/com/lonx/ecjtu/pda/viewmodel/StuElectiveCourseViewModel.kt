package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.service.StuElectiveService
import com.lonx.ecjtu.pda.service.StudentElectiveCourses
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StuElectiveUiState(
    val isLoading: Boolean = false,
    val electiveCourses: StudentElectiveCourses? = null,
    val error: String? = null // 用于显示错误消息
): BaseUiState

class StuElectiveViewModel(
    override val service: StuElectiveService,
    override val prefs: PreferencesManager
):BaseViewModel,ViewModel() {
    private val _uiState = MutableStateFlow(StuElectiveUiState())
    override val uiState = _uiState.asStateFlow()

    fun loadElectiveCourses() {

        if (_uiState.value.isLoading) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = service.getElectiveCourses()) {
                is ServiceResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, electiveCourses = result.data) }
                }
                is ServiceResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
    fun retryLoadElectiveCourses() {
        _uiState.value = StuElectiveUiState()
        loadElectiveCourses()
    }
}