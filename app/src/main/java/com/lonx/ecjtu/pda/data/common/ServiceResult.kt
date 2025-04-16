package com.lonx.ecjtu.pda.data.common


sealed class ServiceResult<out T> {
    data class Success<out T>(val data: T) : ServiceResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : ServiceResult<Nothing>() {
        constructor(message: String) : this(message, null)
    }
}

