package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.LoginResult
import com.lonx.ecjtu.pda.data.NavigationTarget
import com.lonx.ecjtu.pda.data.SplashUiState
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

class SplashViewModel(
    override val service: JwxtService, // Assuming JwxtService has checkSession and login
    override val prefs: PreferencesManager  // Assuming PreferencesManager has hasCredentials
) : ViewModel(), BaseViewModel {

    private val _uiState = MutableStateFlow(SplashUiState(isLoading = true, message = "正在加载...")) // Start with loading true and initial message
    override val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    // Optional: Minimum display time for the splash screen
    private val minSplashTimeMillis = 1500L // 1.5 seconds

    init {
        Timber.tag("SplashVM").d("初始化 SplashViewModel")
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var finalNavigationTarget: NavigationTarget? = null
            var finalMessage: String = ""
            var shouldBeLoading = true // Keep loading until the very end by default

            try {
                // --- Stage 1: Basic Checks (Combine messages) ---
                _uiState.update { it.copy(isLoading = true, message = "正在准备应用...", navigationEvent = null) }


                val hasCreds = prefs.hasCredentials()

                if (!hasCreds) {
                    Timber.tag("SplashVM").d("未找到本地凭据")
                    finalMessage = "请先登录" // Simplified message
                    finalNavigationTarget = NavigationTarget.LOGIN
                    shouldBeLoading = false
                } else {
                    Timber.tag("SplashVM").d("检查会话有效性...")
                    _uiState.update { it.copy(message = "正在验证登录...") }

                    val isLoggedIn = service.checkSession()
                    Timber.tag("SplashVM").d("会话有效性: $isLoggedIn")

                    if (isLoggedIn) {
                        Timber.tag("SplashVM").d("会话有效，准备进入主页")
                        finalMessage = "登录成功，正在进入..."
                        finalNavigationTarget = NavigationTarget.MAIN
                        shouldBeLoading = false
                    } else {
                        Timber.tag("SplashVM").d("会话无效或过期，尝试自动登录...")
                        _uiState.update { it.copy(message = "正在尝试自动登录...") }

                        val loginResult = service.login()

                        when (loginResult) {
                            is LoginResult.Success -> {
                                Timber.tag("SplashVM").d("自动登录成功")
                                finalMessage = "自动登录成功，正在进入..."
                                finalNavigationTarget = NavigationTarget.MAIN
                                shouldBeLoading = false
                            }
                            is LoginResult.Failure -> {
                                Timber.tag("SplashVM").d("自动登录失败: ${loginResult.error}")
                                finalMessage = "自动登录失败，请重新登录"
                                finalNavigationTarget = NavigationTarget.LOGIN
                                shouldBeLoading = false
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                Timber.tag("SplashVM").d("协程被取消")
                throw e // Re-throw cancellation exceptions
            } catch (t: Throwable) {
                Timber.tag("SplashVM").e(t, "启动检查时发生错误")
                finalMessage = "加载出错，请稍后重试" // User-friendly error message
                finalNavigationTarget = NavigationTarget.LOGIN // Default to login on error, or handle differently
                shouldBeLoading = false
            } finally {
                // --- Final Update and Minimum Time Check ---
                Timber.tag("SplashVM").d("检查流程结束. Final Message: '$finalMessage', Target: $finalNavigationTarget")

                // Ensure minimum display time before updating the state for navigation
                splashTime(startTime, minSplashTimeMillis)

                // Update the state *once* at the end with the final result
                if (finalNavigationTarget != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = shouldBeLoading, // Usually false now
                            message = finalMessage,
                            navigationEvent = finalNavigationTarget // Trigger navigation
                        )
                    }
                    Timber.tag("SplashVM").d("Final state updated, navigation triggered.")
                } else {
                    // Handle cases where no navigation is needed but process finished (e.g., show error permanently on splash?)
                    _uiState.update {
                        it.copy(
                            isLoading = false, // Stop loading
                            message = finalMessage // Show final message (e.g., error)
                        )
                    }
                    Timber.tag("SplashVM").d("Final state updated, no navigation.")
                }
            }
        }
    }

    private suspend fun splashTime(startTime: Long, minDuration: Long) {
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = minDuration - elapsedTime
        if (remainingTime > 0) {
            Timber.tag("SplashVM").d("Ensuring minimum splash time, delaying for $remainingTime ms")
            delay(remainingTime)
        }
    }

    fun onNavigationComplete() {
        _uiState.update { it.copy(navigationEvent = null) }
        Timber.tag("SplashVM").d("Navigation event reset.")
    }
}
