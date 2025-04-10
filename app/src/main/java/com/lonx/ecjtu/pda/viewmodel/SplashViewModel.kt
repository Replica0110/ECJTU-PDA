package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.NavigationTarget
import com.lonx.ecjtu.pda.data.ServiceResult
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

data class SplashUiState(
    val isLoading: Boolean = true,
    val message:String = "",
    val navigationEvent: NavigationTarget? = null
): BaseUiState

class SplashViewModel(
    override val service: JwxtService,
    override val prefs: PreferencesManager
) : ViewModel(), BaseViewModel {

    private val _uiState = MutableStateFlow(SplashUiState(isLoading = true, message = "正在加载..."))
    override val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    private val minSplashTimeMillis = 1500L

    init {
        Timber.tag("SplashVM").d("初始化 SplashViewModel")
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var finalNavigationTarget: NavigationTarget? = null
            var finalMessage = ""
            var shouldBeLoading = true

            try {
                _uiState.update { it.copy(isLoading = true, message = "正在准备应用...", navigationEvent = null) }

                val hasCreds = prefs.hasCredentials()

                if (!hasCreds) {
                    Timber.tag("SplashVM").d("未找到本地凭据")
                    finalMessage = "请先登录"
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

                        when (val loginResult = service.login()) {
                            is ServiceResult.Success -> {
                                Timber.tag("SplashVM").d("自动登录成功")
                                finalMessage = "自动登录成功，正在进入..."
                                finalNavigationTarget = NavigationTarget.MAIN
                                shouldBeLoading = false
                            }
                            is ServiceResult.Error -> {
                                Timber.tag("SplashVM").d("自动登录失败: ${loginResult.message}")
                                finalMessage = "自动登录失败，请重新登录"
                                finalNavigationTarget = NavigationTarget.LOGIN
                                shouldBeLoading = false
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                Timber.tag("SplashVM").d("协程被取消")
                throw e
            } catch (t: Throwable) {
                Timber.tag("SplashVM").e(t, "启动检查时发生错误")
                finalMessage = "加载出错，请稍后重试"
                finalNavigationTarget = NavigationTarget.LOGIN
                shouldBeLoading = false
            } finally {
                Timber.tag("SplashVM").d("检查流程结束. Final Message: '$finalMessage', Target: $finalNavigationTarget")
                splashTime(startTime, minSplashTimeMillis)

                if (finalNavigationTarget != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = shouldBeLoading,
                            message = finalMessage,
                            navigationEvent = finalNavigationTarget
                        )
                    }
                    Timber.tag("SplashVM").d("Final state updated, navigation triggered.")
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = finalMessage
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
            delay(remainingTime)
        }
    }

    fun onNavigationComplete() {
        _uiState.update { it.copy(navigationEvent = null) }
    }
}
