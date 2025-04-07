package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.StudentScoreData
import com.lonx.ecjtu.pda.service.StuScoreService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StuScoreUiState(
    val isLoading: Boolean = false,
    val studentScoreData: StudentScoreData? = null,
    val error: String? = null
): BaseUiState

class StuScoreViewModel(
    override val service: StuScoreService,
    override val prefs: PreferencesManager
): ViewModel(),BaseViewModel {
    private val _uiState = MutableStateFlow(StuScoreUiState())
    override val uiState: StateFlow<StuScoreUiState> = _uiState.asStateFlow()

    private var lastRequestedItem: String = "0401"
    fun loadScores(item: String = "0401") {
        lastRequestedItem = item // Store the requested item
        viewModelScope.launch {
            // Set loading state
            _uiState.update { it.copy(isLoading = true, error = null, studentScoreData = null) }

            // Call the service
            when (val result = service.getStudentScoresData(item)) {
                is ServiceResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            studentScoreData = result.data,
                            error = null
                        )
                    }
                }
                is ServiceResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            studentScoreData = null,
                            error = result.message
                        )
                    }
                }
            }
        }
    }
    /**
     * Retries loading the scores using the last requested item code.
     */
    fun retryLoadScores() {
        loadScores(lastRequestedItem)
    }
}