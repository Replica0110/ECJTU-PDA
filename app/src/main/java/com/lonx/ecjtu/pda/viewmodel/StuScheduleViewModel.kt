package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.FullScheduleResult
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.service.StuScheduleService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StuSchedulesUiState(
    val isLoading: Boolean = false,
    val schedulesData: List<FullScheduleResult> ? = null,
    val error: String? = null // 用于显示错误消息
): BaseUiState

class StuScheduleViewModel(
    override val service: StuScheduleService,
    override val prefs: PreferencesManager
):ViewModel(), BaseViewModel {
    private val _uiState = MutableStateFlow(StuSchedulesUiState())
    override val uiState = _uiState.asStateFlow()

    fun loadSchedules() {
        if (_uiState.value.isLoading) {
            return
        }
        viewModelScope.launch{
            _uiState.value = _uiState.value.copy(isLoading = true, schedulesData = null , error = null)
            when (val result = service.getAllSchedules()) {
                is ServiceResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        schedulesData = result.data
                    )
                }

                is ServiceResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }
    fun retryLoadSchedules() {
        _uiState.value = StuSchedulesUiState()
        loadSchedules()
    }
}