package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.IspOption
import com.lonx.ecjtu.pda.data.model.availableIsp
import com.lonx.ecjtu.pda.domain.usecase.GetStuCredentialsUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetWeiXinIDUseCase
import com.lonx.ecjtu.pda.domain.usecase.LogoutUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdatePasswordUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdateStuCredentialsUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdateWeiXinIDUseCase
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
    private val updatePasswordUseCase: UpdatePasswordUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val updateWeiXinIDUseCase: UpdateWeiXinIDUseCase,
    private val getStuCredentialsUseCase: GetStuCredentialsUseCase,
    private val getWeiXinIDUseCase: GetWeiXinIDUseCase,
    private val updateStuCredentialsUseCase: UpdateStuCredentialsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingUiState())
    val uiState: StateFlow<SettingUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<SettingUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                val credentials = getStuCredentialsUseCase()
                val savedId = credentials.first
                val savedPassword = credentials.second
                val savedIspId = credentials.third
                val savedWeiXinId = getWeiXinIDUseCase()
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
                updateStuCredentialsUseCase(studentId, password, selectedIsp.id)
                Timber.i("保存配置: ID=$studentId, Pass=***, ISP=${selectedIsp.name}")

                _uiState.update {
                    it.copy(
                        studentId = studentId,
                        password = password, // 更新UI状态中的密码
                        ispSelected = selectedIsp,
                        isLoading = false
                    )
                }
                _uiEvent.emit(SettingUiEvent.ShowSnackbar("账号信息保存成功", true))
            } catch (e: Exception) {
                Timber.e(e, "保存配置失败")
                _uiState.update { it.copy(isLoading = false, error = "保存失败: ${e.message}") }
                // 发送失败提示
                _uiEvent.emit(SettingUiEvent.ShowSnackbar("账号信息保存失败: ${e.message}", false))
            }
        }
    }

    /**修改智慧交大密码*/
    fun updatePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) } // 开始加载
            var operationSuccessful = false
            var feedbackMessage = ""

            try {
                // 调用更新后的 service 方法
                when (val result = updatePasswordUseCase(oldPassword, newPassword)) {
                    is PDAResult.Success -> {
                        Timber.i("密码更新成功 (Service层)")
                        feedbackMessage = "密码修改成功"
                        operationSuccessful = true
                    }
                    is PDAResult.Error -> {
                        Timber.w("密码更新失败: ${result.message}")
                        feedbackMessage = result.message
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "调用更新密码服务时发生异常")
                feedbackMessage = "更新密码时发生错误: ${e.message ?: "未知错误"}"
            } finally {
                if (feedbackMessage.isNotBlank()) {
                    _uiEvent.emit(SettingUiEvent.ShowSnackbar(feedbackMessage, operationSuccessful))
                }

                if (operationSuccessful) {
                    try {
                        val currentState = _uiState.value
                        updateStuCredentialsUseCase(currentState.studentId, newPassword, currentState.ispSelected.id)
                        Timber.i("新密码已成功保存到本地")

                        _uiState.update { it.copy(password = newPassword, isLoading = false) }

                        _uiEvent.emit(SettingUiEvent.CloseDialog)

                    } catch (saveError: Exception) {
                        Timber.e(saveError, "服务器密码更新成功，但本地保存失败！")
                        _uiEvent.emit(SettingUiEvent.ShowSnackbar("密码已在服务器更新，但本地保存失败。", false))
                        _uiState.update { it.copy(isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    /**更新微信ID*/
    fun updateWeiXinId(weiXinId: String){
        viewModelScope.launch{
            try {
                updateWeiXinIDUseCase(weiXinId)
                _uiState.update { it.copy(weiXinId = weiXinId) }
                _uiEvent.emit(SettingUiEvent.ShowSnackbar("微信ID保存成功", true))
            } catch (e: Exception) {
                Timber.e(e, "保存 weixinid 失败")
                _uiEvent.emit(SettingUiEvent.ShowSnackbar("微信ID保存失败: ${e.message}", false))
            }
        }
    }
    /**清空登录信息*/
    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                logoutUseCase(clearStoredCredentials = true)
                _uiState.value = SettingUiState()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "退出登录失败")
                _uiState.update { it.copy( isLoading = false, error = "退出登录失败: ${e.message}")}
            }
        }
    }
}