package com.lonx.ecjtu.pda.data


sealed interface ServiceResult<out T> {
    data class Success<T>(val data: T) : ServiceResult<T>
    data class Error(val message: String, val exception: Exception? = null) : ServiceResult<Nothing>
}