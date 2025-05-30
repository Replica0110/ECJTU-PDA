package com.lonx.ecjtu.pda.state

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.common.toUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


fun <T> CoroutineScope.launchUiState(
    state: MutableStateFlow<UiState<T>>,
    block: suspend () -> PDAResult<T>
) {
    state.value = UiState.Loading
    launch {
        val result = block()
        state.value = result.toUiState()
    }
}