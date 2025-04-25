package com.lonx.ecjtu.pda.data.common

import com.lonx.ecjtu.pda.state.UiState
import timber.log.Timber



inline fun <T> PDAResult<T>.onSuccess(action: (T) -> Unit): PDAResult<T> {
    if (this is PDAResult.Success) action(data)
    return this
}

inline fun <T> PDAResult<T>.onError(action: (String, Exception?) -> Unit): PDAResult<T> {
    if (this is PDAResult.Error) action(message, exception)
    return this
}

fun <T> PDAResult<T>.getOrNull(): T? = (this as? PDAResult.Success)?.data

fun <T> PDAResult<T>.getOrElse(defaultValue: T): T = getOrNull() ?: defaultValue

inline fun <T, R> PDAResult<T>.map(transform: (T) -> R): PDAResult<R> = when (this) {
    is PDAResult.Success -> PDAResult.Success(transform(data))
    is PDAResult.Error -> this
}


// 扩展函数：将 ServiceResult 转为 UiState
fun <T> PDAResult<T>.toUiState(): UiState<T> = when (this) {
    is PDAResult.Success -> UiState.Success(data)
    is PDAResult.Error -> UiState.Error(message, exception)
}

inline fun <T, R> PDAResult<T>.mapCatching(transform: (T) -> R): PDAResult<R> = try {
    when (this) {
        is PDAResult.Success -> PDAResult.Success(transform(data))
        is PDAResult.Error -> this
    }
} catch (e: Exception) {
    PDAResult.Error("映射异常", e)
}

fun <T> PDAResult<T>.log(tag: String = "ServiceResult"): PDAResult<T> {
    when (this) {
        is PDAResult.Success -> Timber.tag(tag).d("请求成功")
        is PDAResult.Error -> Timber.tag(tag).e(exception, "请求失败: $message")
    }
    return this
}
