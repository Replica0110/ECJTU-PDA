package com.lonx.ecjtu.pda.state

sealed class UiState<out T> {
    data object Empty : UiState<Nothing>() // 初始状态，表示没有数据
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val exception: Throwable? = null) : UiState<Nothing>()
}