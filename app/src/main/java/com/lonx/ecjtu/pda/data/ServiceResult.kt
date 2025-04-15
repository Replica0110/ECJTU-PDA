package com.lonx.ecjtu.pda.data

import com.lonx.ecjtu.pda.state.UiState
import timber.log.Timber


sealed class ServiceResult<out T> {
    data class Success<out T>(val data: T) : ServiceResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : ServiceResult<Nothing>() {
        constructor(message: String) : this(message, null)
    }
}

inline fun <T> ServiceResult<T>.onSuccess(action: (T) -> Unit): ServiceResult<T> {
    if (this is ServiceResult.Success) action(data)
    return this
}

inline fun <T> ServiceResult<T>.onError(action: (String, Exception?) -> Unit): ServiceResult<T> {
    if (this is ServiceResult.Error) action(message, exception)
    return this
}

fun <T> ServiceResult<T>.getOrNull(): T? = (this as? ServiceResult.Success)?.data

fun <T> ServiceResult<T>.getOrElse(defaultValue: T): T = getOrNull() ?: defaultValue

inline fun <T, R> ServiceResult<T>.map(transform: (T) -> R): ServiceResult<R> = when (this) {
    is ServiceResult.Success -> ServiceResult.Success(transform(data))
    is ServiceResult.Error -> this
}


// 扩展函数：将 ServiceResult 转为 UiState
fun <T> ServiceResult<T>.toUiState(): UiState<T> = when (this) {
    is ServiceResult.Success -> UiState.Success(data)
    is ServiceResult.Error -> UiState.Error(message, exception)
}

inline fun <T, R> ServiceResult<T>.mapCatching(transform: (T) -> R): ServiceResult<R> = try {
    when (this) {
        is ServiceResult.Success -> ServiceResult.Success(transform(data))
        is ServiceResult.Error -> this
    }
} catch (e: Exception) {
    ServiceResult.Error("映射异常", e)
}

fun <T> ServiceResult<T>.log(tag: String = "ServiceResult"): ServiceResult<T> {
    when (this) {
        is ServiceResult.Success -> Timber.tag(tag).d("成功: $data")
        is ServiceResult.Error -> Timber.tag(tag).e(exception, "失败: $message")
    }
    return this
}
