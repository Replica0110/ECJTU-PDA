package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import com.lonx.ecjtu.pda.repository.source.JwxtApiClientImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

abstract class BaseJwxtRepository(
    protected val apiClient: JwxtApiClient,
    val authRepository: AuthRepository
) {
    companion object {
        private val apiReLoginMutex = Mutex()
    }

    /**
     * 使用ApiClient获取数据的包装函数，并在捕获到SessionExpiredException时尝试重新登录。
     * 此方法是protected，因此只有子类可以使用它。
     *
     * @param fetchAction 使用[apiClient]执行实际API调用的挂起函数。
     * @return 从fetch操作返回的[PDAResult]，可能在成功重新登录和重试后返回。
     */
    protected suspend fun getHtml(
        fetchAction: suspend () -> PDAResult<String>
    ): PDAResult<String> = withContext(Dispatchers.IO) {
        val initialResult = fetchAction()

        // 检查是否抛出了特定的会话过期异常
        if (initialResult is PDAResult.Error && initialResult.exception is JwxtApiClientImpl.SessionExpiredException) {
            Timber.e("Repository检测到会话已过期。正在尝试通过AuthRepository重新登录。")

            apiReLoginMutex.withLock {
                // 在锁内再次检查会话有效性，避免重复登录
                if (authRepository.checkSessionValidity()) {
                    Timber.e("在尝试重新登录前会话已变为有效。正在重试获取数据。")
                    return@withContext fetchAction() // 立即重试
                }

                // 尝试使用AuthRepository进行重新登录
                val loginResult = authRepository.login(forceRefresh = true)

                if (loginResult is PDAResult.Success) {
                    Timber.e("重新登录成功。正在重试原始数据获取。")
                    return@withContext fetchAction() // 登录成功后重试获取数据
                } else {
                    Timber.e("重新登录失败: ${(loginResult as PDAResult.Error).message}。传播原始会话错误。")
                    return@withContext initialResult // 返回原始的会话错误
                }
            } // 结束互斥锁
        } else {
            // 如果不是会话错误或操作成功，则返回结果
            return@withContext initialResult
        }
    }

    open class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)
}
