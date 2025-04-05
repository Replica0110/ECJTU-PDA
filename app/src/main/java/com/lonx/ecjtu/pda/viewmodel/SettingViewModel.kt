package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.IspOption
import com.lonx.ecjtu.pda.data.SettingUiState
import com.lonx.ecjtu.pda.data.availableIsp
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class SettingUiState(
    val studentId: String = "",
    val password: String = "",
    val ispSelected: IspOption = IspOption(1, "中国移动"),
    val isLoading: Boolean = false,
    val error: String? = null
): BaseUiState
class SettingViewModel(
    override val service: JwxtService,
    override val prefs: PreferencesManager
) : ViewModel() , BaseViewModel {
    private val _uiState = MutableStateFlow(SettingUiState())
    override val uiState: StateFlow<SettingUiState> = _uiState.asStateFlow()
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
    fun updatePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = service.updatePassword(oldPassword, newPassword, confirmPassword)
                Timber.d("Password updated successfully")
                _uiState.update { it.copy(isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy()}
            }
        }
    }
}