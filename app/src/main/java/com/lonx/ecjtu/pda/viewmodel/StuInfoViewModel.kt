package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.StuInfoUiState
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StuInfoViewModel(
    override val service: JwxtService,
    override val prefs: PreferencesManager
): ViewModel(), BaseViewModel {
    private val _uiState = MutableStateFlow(StuInfoUiState())
    override val uiState: StateFlow<StuInfoUiState> = _uiState.asStateFlow()
    fun loadStudentInfo() {
        // 防止重复加载，如果已经在加载中则直接返回
        if (_uiState.value.isLoading) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) } // 开始加载，清除旧错误

            when (val result = service.getStuInfo()) {
                is ServiceResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            studentInfo = result.data,
                            error = null
                        )
                    }
                }
                is ServiceResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            studentInfo = null, // 清除可能存在的旧数据
                            error = result.message // 使用 ServiceResult 中的错误消息
                        )
                    }
                }
            }
        }
    }
    /**
     * 触发退出登录流程。
     * 调用 Service 执行核心登出操作，并重置本地 UI 状态。
     */
    fun performLogout() {
        viewModelScope.launch {
            service.logout()

            _uiState.value = StuInfoUiState()
        }
    }
    // 可选：提供一个重试方法
    fun retryLoadStudentInfo() {
        // 重置状态并重新加载
        _uiState.value = StuInfoUiState()
        loadStudentInfo()
    }
}