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

    // 最短启动画面显示时间
    private val minSplashTimeMillis = 1500L

    init {
        Timber.tag("SplashVM").d("初始化 SplashViewModel")
        checkAuthStatus() // 开始检查认证状态
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis() // 记录开始时间
            var finalNavigationTarget: NavigationTarget? = null // 最终导航目标
            var finalMessage = "" // 最终显示给用户的消息

            try {
                // 初始状态：加载中，准备应用
                _uiState.update { it.copy(isLoading = true, message = "正在准备应用...", navigationEvent = null) }

                // 检查本地是否存有凭据
                val hasCreds = prefs.hasCredentials() // 假设 prefs 有此方法

                if (!hasCreds) {
                    // 情况 1: 本地没有凭据 -> 跳转登录页
                    Timber.tag("SplashVM").d("未找到本地凭据")
                    finalMessage = "请先登录"
                    finalNavigationTarget = NavigationTarget.LOGIN
                } else {
                    // 情况 2: 本地有凭据 -> 尝试确保登录状态
                    Timber.tag("SplashVM").d("找到本地凭据，尝试确保登录状态...")
                    _uiState.update { it.copy(message = "正在验证登录...") } // 更新提示信息

                    // ensureLogin 会检查会话，如果无效会尝试自动登录
                    when (val loginResult = service.ensureLogin()) {
                        is ServiceResult.Success -> {
                            // 成功: 会话有效 或 自动登录成功 -> 跳转主页
                            Timber.tag("SplashVM").d("登录状态有效或自动登录成功")
                            finalMessage = "验证成功，正在进入..."
                            finalNavigationTarget = NavigationTarget.MAIN
                        }
                        is ServiceResult.Error -> {
                            // 失败: 会话无效 且 自动登录失败 -> 跳转登录页
                            Timber.tag("SplashVM").w("登录状态无效且自动登录失败: ${loginResult.message}")
                            // 可以向用户显示更具体的失败原因
                            finalMessage = "登录验证失败，请重新登录"
                            finalNavigationTarget = NavigationTarget.LOGIN
                        }
                    }
                }

            } catch (e: CancellationException) {
                // 捕获协程取消异常，重新抛出
                Timber.tag("SplashVM").w("启动检查协程被取消")
                throw e
            } catch (t: Throwable) {
                // 捕获其他所有未知错误
                Timber.tag("SplashVM").e(t, "启动检查时发生未知错误")
                finalMessage = "加载出错，请稍后重试" // 通用错误提示
                finalNavigationTarget = NavigationTarget.LOGIN // 发生未知错误时，通常也导向登录页
            } finally {
                // --- 确保最短启动时间并在最后更新UI ---
                Timber.tag("SplashVM").d("检查流程结束. 最终消息: '$finalMessage', 导航目标: $finalNavigationTarget")

                // 确保最短显示时间
                splashTime(startTime, minSplashTimeMillis)

                // 在最后一次性更新UI状态，包含最终的消息和导航事件
                _uiState.update {
                    it.copy(
                        isLoading = false, // 无论结果如何，加载过程都结束了
                        message = finalMessage, // 显示最终的消息
                        navigationEvent = finalNavigationTarget // 设置导航事件，触发导航
                    )
                }
                Timber.tag("SplashVM").d("最终状态已更新，导航事件已设置 (如果目标非null)。")
            }
        }
    }

    /**
     * 确保从 startTime 开始至少经过 minDuration 毫秒。
     */
    private suspend fun splashTime(startTime: Long, minDuration: Long) {
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = minDuration - elapsedTime
        if (remainingTime > 0) {
            Timber.tag("SplashVM").d("确保最短启动时间，延迟 $remainingTime ms")
            delay(remainingTime)
        }
    }

    /**
     * 当导航事件被消耗后，由UI层调用以重置导航事件。
     */
    fun onNavigationComplete() {
        _uiState.update { it.copy(navigationEvent = null) }
        Timber.tag("SplashVM").d("导航事件已重置。")
    }
}