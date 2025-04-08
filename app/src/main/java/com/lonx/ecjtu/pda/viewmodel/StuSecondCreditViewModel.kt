package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.service.SecondCreditData
import com.lonx.ecjtu.pda.service.StuSecondCreditService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StuSecondCreditUiState(
    val isLoading: Boolean = false,
    val secondCreditData: SecondCreditData? = null,
    val error: String? = null
): BaseUiState

class StuSecondCreditViewModel(
    override val service: StuSecondCreditService,
    override val prefs: PreferencesManager
): ViewModel(), BaseViewModel {
    private val _uiState =  MutableStateFlow(StuSecondCreditUiState())
    override val uiState = _uiState.asStateFlow()
    fun loadSecondCredit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, secondCreditData = null) }
            when (val result = service.getSecondCredit()) {
                is ServiceResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, error = null, secondCreditData = result.data) }
                }
                is ServiceResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message, secondCreditData = null) }
                }
            }
        }
    }
    fun retryLoadSecondCredit() {
        loadSecondCredit()
    }
}