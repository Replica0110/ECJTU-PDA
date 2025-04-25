package com.lonx.ecjtu.pda.data.common


sealed class PDAResult<out T> {
    data class Success<out T>(val data: T) : PDAResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : PDAResult<Nothing>() {
        constructor(message: String) : this(message, null)
    }
}

