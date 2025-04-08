package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.CardType
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val card: CardType? = null,
    val error: String? = null
): BaseUiState

class HomeViewModel(
    override val service: JwxtService,
    override val prefs: PreferencesManager
) : ViewModel(), BaseViewModel {
    private val _uiState = MutableStateFlow(HomeUiState())
    override val uiState:StateFlow<HomeUiState> = _uiState.asStateFlow()
    fun loadHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(card = CardType.TEST) }
        }
    }
    fun performLogout() {
        viewModelScope.launch {
            service.logout()
            _uiState.value = HomeUiState()
        }
    }
}