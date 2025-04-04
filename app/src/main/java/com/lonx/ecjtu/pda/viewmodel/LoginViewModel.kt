package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.LoginResult
import com.lonx.ecjtu.pda.data.LoginUiState
import com.lonx.ecjtu.pda.data.NavigationTarget
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber


class LoginViewModel(
    override val service: JwxtService,
    override val prefs: PreferencesManager
) : ViewModel(), BaseViewModel {

    private val _uiState = MutableStateFlow(LoginUiState())
    override val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
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
                val result = service.loginManually(
                    studentId = currentState.studentId,
                    studentPass = currentState.password
                )

                if (result is LoginResult.Success) {
                    Timber.i("ViewModel: Manual login successful, navigating to Main.")
                    _uiState.update { it.copy(isLoading = false, navigationEvent = NavigationTarget.MAIN) }
                } else {
                    val errorMsg = (result as LoginResult.Failure).error
                    Timber.w("ViewModel: Manual login failed: $errorMsg")
                    _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Login attempt failed with unexpected exception")
                _uiState.update { it.copy(isLoading = false, error = "发生意外错误: ${e.message}") }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigationEvent = null) }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(error = null) }
    }
}
