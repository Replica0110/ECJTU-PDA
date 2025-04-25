package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.data.common.NavigationTarget
import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.domain.usecase.CheckCredentialsExistUseCase
import com.lonx.ecjtu.pda.domain.usecase.CheckSessionValidityUseCase
import com.lonx.ecjtu.pda.domain.usecase.LoginUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

data class SplashUiState(
    val isLoading: Boolean = true,
    val message:String = "",
    val navigationEvent: NavigationTarget? = null
): BaseUiState

class SplashViewModel(
    private val checkCredentialsExistUseCase: CheckCredentialsExistUseCase,
    private val checkSessionValidityUseCase: CheckSessionValidityUseCase,
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    private val minSplashTimeMillis = 1500L
    private val logTag = "SplashVM"

    init {
        Timber.tag(logTag).d("初始化 SplashViewModel")
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var finalNavigationTarget: NavigationTarget? = null
            var finalMessage = "正在准备应用..."
            var shouldBeLoading = true

            _uiState.update { it.copy(isLoading = true, message = finalMessage, navigationEvent = null) }

            try {
                val hasCreds = checkCredentialsExistUseCase()
                Timber.tag(logTag).d("本地凭据检查结果: $hasCreds")

                if (!hasCreds) {
                    finalMessage = "请先登录"
                    finalNavigationTarget = NavigationTarget.LOGIN
                    shouldBeLoading = false
                } else {
                    _uiState.update { it.copy(message = "正在验证登录...") }
                    val isSessionValid = checkSessionValidityUseCase()
                    Timber.tag(logTag).d("会话有效性检查结果: $isSessionValid")

                    if (isSessionValid) {
                        finalMessage = "登录成功，正在进入..."
                        finalNavigationTarget = NavigationTarget.MAIN
                        shouldBeLoading = false
                    } else {
                        _uiState.update { it.copy(message = "正在尝试自动登录...") }
                        when (val loginResult = loginUseCase()) {
                            is PDAResult.Success -> {
                                Timber.tag(logTag).d("自动登录成功")
                                finalMessage = "自动登录成功，正在进入..."
                                finalNavigationTarget = NavigationTarget.MAIN
                                shouldBeLoading = false
                            }
                            is PDAResult.Error -> {
                                Timber.tag(logTag).w("自动登录失败: ${loginResult.message}")
                                finalMessage = "自动登录失败，请重新登录"
                                finalNavigationTarget = NavigationTarget.LOGIN
                                shouldBeLoading = false
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                Timber.tag(logTag).d("启动检查协程被取消")
                throw e
            } catch (t: Throwable) {
                Timber.tag(logTag).e(t, "启动检查时发生意外错误")
                finalMessage = "加载出错，请稍后重试"
                finalNavigationTarget = NavigationTarget.LOGIN
                shouldBeLoading = false
            } finally {
                Timber.tag(logTag).d("检查流程结束. Final Message: '$finalMessage', Target: $finalNavigationTarget")

                splashTime(startTime, minSplashTimeMillis)
                Timber.tag(logTag).d("最短启动时间已满足")

                _uiState.update {
                    it.copy(
                        isLoading = shouldBeLoading,
                        message = finalMessage,
                        navigationEvent = finalNavigationTarget
                    )
                }
                Timber.tag(logTag).d("最终状态已更新 (Loading: $shouldBeLoading). Navigation Target: $finalNavigationTarget")
            }
        }
    }

    /** Ensures the splash screen is shown for a minimum duration. */
    private suspend fun splashTime(startTime: Long, minDuration: Long) {
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = minDuration - elapsedTime
        if (remainingTime > 0) {
            Timber.tag(logTag).d("需要等待 ${remainingTime}ms 以满足最短启动时间")
            delay(remainingTime)
        }
    }

    /** Call this from the UI after navigation has occurred. */
    fun onNavigationComplete() {
        Timber.tag(logTag).d("导航事件处理完成，清除 navigationEvent")
        _uiState.update { it.copy(navigationEvent = null) }
    }
}
