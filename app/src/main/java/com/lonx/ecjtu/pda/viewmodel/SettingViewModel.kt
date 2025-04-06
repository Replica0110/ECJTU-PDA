package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.IspOption
import com.lonx.ecjtu.pda.data.ServiceResult
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
    data class ShowSnackbar(val message: String,val success: Boolean) : SettingUiEvent()

    data object CloseDialog : SettingUiEvent()
}

data class SettingUiState(
    val studentId: String = "",
    val password: String = "",
    val ispSelected: IspOption = IspOption(1, "中国移动"),
    val weiXinId:String = "",
    val isLoading: Boolean = false,
    val error: String? = null
): BaseUiState


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
                val savedWeiXinId = prefs.getWeiXinId()
                val selectedIsp = availableIsp.find { it.id == savedIspId } ?: currentState.ispSelected
                currentState.copy(
                    studentId = savedId,
                    password = savedPassword,
                    ispSelected = selectedIsp,
                    weiXinId = savedWeiXinId
                )
            }
        }
    }
    /**账号密码及运营商配置*/
    fun updateConfig(studentId: String, password: String, selectedIsp: IspOption) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                 prefs.saveCredentials(studentId, password, selectedIsp.id)
                Timber.e("Saving: ID=$studentId, Pass=***, ISP=${selectedIsp.name}")

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

            try {
                when (val result = service.updatePassword(oldPassword, newPassword)) {
                    is ServiceResult.Success -> {
                        Timber.i("Password update successful: ${result.data}")
                        feedbackMessage = result.data
                        operationSuccessful = true
                    }
                    is ServiceResult.Error -> {
                        Timber.w("Password update failed: ${result.message}")
                        feedbackMessage = result.message
                    }
                }
            } catch (e: Exception) {
                feedbackMessage = "更新密码时发生错误: ${e.message ?: "未知错误"}"
            } finally {
                if (feedbackMessage.isNotBlank()) {
                    _uiEvent.emit(SettingUiEvent.ShowSnackbar(feedbackMessage, operationSuccessful))
                }

                if (operationSuccessful) {
                    try {
                        val currentState = _uiState.value
                        prefs.saveCredentials(currentState.studentId, newPassword, currentState.ispSelected.id)

                        _uiState.update { it.copy(password = newPassword, isLoading = false) }

                        _uiEvent.emit(SettingUiEvent.CloseDialog)

                    } catch (saveError: Exception) {
                        Timber.e(saveError, "Failed to save new password locally after successful server update!")
                        _uiEvent.emit(SettingUiEvent.ShowSnackbar("密码已在服务器更新，但本地保存失败。", false))
                        _uiState.update { it.copy(isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
    fun updateWeiXinId(weiXinId: String){
        viewModelScope.launch{
            try {
                prefs.setWeiXinId(weiXinId)
                _uiState.update { it.copy(weiXinId = weiXinId) }
                _uiEvent.emit(SettingUiEvent.ShowSnackbar("保存成功", true))
            } catch (e: Exception) {
                Timber.e(e, "保存weixinid失败")
                _uiEvent.emit(SettingUiEvent.ShowSnackbar("保存失败", false))
            }
        }

    }
}
