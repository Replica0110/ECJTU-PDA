package com.lonx.ecjtu.pda.state

import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.toUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


// 快速挂载在 ViewModel 中
fun <T> CoroutineScope.launchUiState(
    state: MutableStateFlow<UiState<T>>,
    block: suspend () -> ServiceResult<T>
) {
    state.value = UiState.Loading
    launch {
        val result = block()
        state.value = result.toUiState()
    }
}