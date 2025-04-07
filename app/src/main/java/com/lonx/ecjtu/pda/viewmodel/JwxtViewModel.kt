package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.service.StuScoreService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JwxtUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
):BaseUiState

class JwxtViewModel(
    private val jwxtService: JwxtService,
    private val stuScoreService: StuScoreService,
    private val prefs: PreferencesManager
):ViewModel() {
    private val _uiState = MutableStateFlow(JwxtUiState(isLoading = false, errorMessage = null))
    val uiState: StateFlow<JwxtUiState> = _uiState.asStateFlow()


}


