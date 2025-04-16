package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.data.common.CardType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    val isLoading: Boolean = false,
    val card: CardType? = null,
    val error: String? = null
): BaseUiState

class HomeViewModel(
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState:StateFlow<HomeUiState> = _uiState.asStateFlow()
}