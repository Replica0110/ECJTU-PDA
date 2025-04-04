package com.lonx.ecjtu.pda.base

import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.StateFlow

interface BaseUiState {}
interface BaseViewModel {
    val service: BaseService
    val prefs: PreferencesManager
    val uiState: StateFlow<BaseUiState>


}