package com.lonx.ecjtu.pda.data

sealed class LoginResult {
    data class Success(val message: String) : LoginResult()
    data class Failure(val error: String) : LoginResult()
}

sealed interface ServiceResult<out T> {
    data class Success<T>(val data: T) : ServiceResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : ServiceResult<Nothing>
}