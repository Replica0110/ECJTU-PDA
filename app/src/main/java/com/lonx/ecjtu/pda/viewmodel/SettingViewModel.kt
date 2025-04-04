package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.SettingUiState
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingViewModel(override val service: JwxtService, override val prefs: PreferencesManager): ViewModel() , BaseViewModel {
    private val _uiState = MutableStateFlow(SettingUiState())
    override val uiState: StateFlow<SettingUiState> = _uiState.asStateFlow()
}