package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.IspOption
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.SettingUiState
import com.lonx.ecjtu.pda.data.availableIsp
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
sealed class SettingUiEvent {
    data class ShowSnackbar(val message: String) : SettingUiEvent()

    data object CloseDialog : SettingUiEvent()
}

class SettingViewModel(
    override val service: JwxtService,
    override val prefs: PreferencesManager
) : ViewModel() , BaseViewModel {

    private val _uiState = MutableStateFlow(SettingUiState())
    override val uiState: StateFlow<SettingUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<SettingUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val credentials = prefs.getCredentials()
                val savedId = credentials.first
                val savedPassword =  credentials.second
                val savedIspId = credentials.third
                val selectedIsp = availableIsp.find { it.id == savedIspId } ?: currentState.ispSelected
                currentState.copy(
                    studentId = savedId,
                    password = savedPassword,
                    ispSelected = selectedIsp
                )
            }
        }
    }
    /**账号密码及运营商配置*/
    fun updateConfig(studentId: String, password: String, selectedIsp: IspOption) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                 service.saveCredentials(studentId, password, selectedIsp.id)
                Timber.d("Saving: ID=$studentId, Pass=***, ISP=${selectedIsp.name}")

                _uiState.update {
                    it.copy(
                        studentId = studentId,
                        password = password,
                        ispSelected = selectedIsp,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "保存失败: ${e.message}") }
            }
        }
    }
    /**修改智慧交大密码*/
    fun updatePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) } // Start loading
            var operationSuccessful = false
            var feedbackMessage = ""

            try { // Add try block for service call
                when (val result = service.updatePassword(oldPassword, newPassword)) {
                    is ServiceResult.Success -> {
                        Timber.i("Password update successful: ${result.data}") // Use Timber.i for info
                        feedbackMessage = result.data ?: "密码修改成功"
                        operationSuccessful = true
                    }
                    is ServiceResult.Error -> {
                        Timber.w("Password update failed: ${result.message}") // Use Timber.w for warning
                        feedbackMessage = result.message ?: "密码修改失败"
                        // Optionally update uiState.error here if needed
                        // _uiState.update { it.copy(error = feedbackMessage) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception during password update service call.")
                feedbackMessage = "更新密码时发生错误: ${e.message ?: "未知错误"}"
                // _uiState.update { it.copy(error = feedbackMessage) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }

                if (feedbackMessage.isNotBlank()) {
                    _uiEvent.emit(SettingUiEvent.ShowSnackbar(feedbackMessage))
                }

                if (operationSuccessful) {
                    _uiEvent.emit(SettingUiEvent.CloseDialog)
                }
            }
        }
    }
}
