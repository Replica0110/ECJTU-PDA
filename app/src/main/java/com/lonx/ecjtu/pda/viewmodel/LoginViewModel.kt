package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.data.common.IspOption
import com.lonx.ecjtu.pda.data.common.NavigationTarget
import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.common.availableIsp
import com.lonx.ecjtu.pda.domain.usecase.LoginManuallyUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class LoginUiState(
    val studentId: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigationEvent: NavigationTarget? = null,
    val ispOptions: List<IspOption> = availableIsp,
    val selectedIspId: Int = 1
): BaseUiState

class LoginViewModel(
    private val loginManuallyUseCase: LoginManuallyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onStudentIdChange(id: String) { _uiState.update { it.copy(studentId = id) } }
    fun onPasswordChange(pass: String) { _uiState.update { it.copy(password = pass) } }
    fun onIspSelected(ispId: Int) { _uiState.update { it.copy(selectedIspId = ispId) } }

    fun attemptLogin() {
        val currentState = _uiState.value
        if (currentState.studentId.isBlank() || currentState.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入账号和密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, navigationEvent = null) }
            try {
                val result = loginManuallyUseCase(
                    studentId = currentState.studentId.trim(),
                    studentPass = currentState.password,
                    ispOption = currentState.selectedIspId
                )

                when (result) {
                    is ServiceResult.Success -> {
                        Timber.i("登录成功，准备导航到主界面")

                        _uiState.update { it.copy(isLoading = false, navigationEvent = NavigationTarget.MAIN) }
                    }
                    is ServiceResult.Error -> {
                        Timber.w("登录失败: ${result.message}")
                        _uiState.update { it.copy(isLoading = false, error = result.message) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "登录尝试因意外异常失败")
                _uiState.update { it.copy(isLoading = false, error = "发生意外错误: ${e.message}") }
            }
        }
    }

    /** 处理导航事件，防止重复导航 */
    fun onNavigationHandled() {
        _uiState.update { it.copy(navigationEvent = null) }
    }

    /** 处理错误消息显示完成事件，清除错误状态 */
    fun onErrorMessageShown() {
        _uiState.update { it.copy(error = null) }
    }
}