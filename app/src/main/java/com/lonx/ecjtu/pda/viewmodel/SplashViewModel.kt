package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.LoginResult
import com.lonx.ecjtu.pda.data.NavigationTarget
import com.lonx.ecjtu.pda.data.SplashUiState
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

class SplashViewModel(override val service: JwxtService, override val prefs: PreferencesManager) : ViewModel(),
    BaseViewModel {
    private val _uiState = MutableStateFlow(SplashUiState())
    override val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        Timber.tag("SplashVM").e("SplashViewModel init")
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        Timber.tag("SplashVM").e("checkAuthStatus() called.") // Use consistent tag
        viewModelScope.launch {
            try {
                Timber.tag("SplashVM").e("Coroutine launched. Updating UI state...") // Log start of coroutine
                _uiState.update { it.copy(isLoading = true, navigationEvent = null) }

                Timber.tag("SplashVM").e("Delaying...")
                kotlinx.coroutines.delay(1000)
                Timber.tag("SplashVM").e("Delay finished. Checking credentials...")

                val hasCreds = prefs.hasCredentials()
                Timber.tag("SplashVM").e("Credentials check result: $hasCreds")

                if (!hasCreds) {
                    Timber.tag("SplashVM").e("No credentials found, navigating to Login.")
                    _uiState.update { it.copy(isLoading = false, navigationEvent = NavigationTarget.LOGIN) }
                    return@launch // Exit coroutine
                }

                Timber.tag("SplashVM").e("Checking login status...")
                val isLoggedIn = service.hasLogin()
                Timber.tag("SplashVM").e("Login status check result: $isLoggedIn")

                if (isLoggedIn) {
                    Timber.tag("SplashVM").e("Existing session valid, navigating to Main.")
                    _uiState.update { it.copy(isLoading = false, navigationEvent = NavigationTarget.MAIN) }
                    return@launch // Exit coroutine
                }

                // Only attempt login if needed
                Timber.tag("SplashVM").d("No valid session, attempting auto-login...")
                val loginResult = service.login() // Assuming service.login() is suspend fun
                Timber.tag("SplashVM").d("Auto-login result: $loginResult")

                when (loginResult) {
                    is LoginResult.Success -> {
                        Timber.tag("SplashVM").e("Auto-login successful, navigating to Main.")
                        _uiState.update { it.copy(isLoading = false, navigationEvent = NavigationTarget.MAIN) }
                    }
                    is LoginResult.Failure -> {
                        Timber.tag("SplashVM").e("Auto-login failed, navigating to Login.")
                        _uiState.update { it.copy(isLoading = false, navigationEvent = NavigationTarget.LOGIN) }
                    }
                }
                Timber.tag("SplashVM").e("Coroutine finished normally.")

            } catch (e: CancellationException) {
                // This is expected if the ViewModel is cleared while the coroutine is running
                Timber.tag("SplashVM").w("Coroutine cancelled (expected if navigating away quickly).")
                Timber.tag("SplashVM").e(e)
                throw e // Re-throw CancellationException is important
            } catch (t: Throwable) { // Catch any other Exception/Throwable
                Timber.tag("SplashVM").e(t, "!!! EXCEPTION IN checkAuthStatus coroutine !!!")
                // Update UI state to show error or navigate to login on unexpected error
                _uiState.update { it.copy(isLoading = false, navigationEvent = NavigationTarget.LOGIN) } // Example: navigate to login on error
            }
        }
        Timber.tag("SplashVM").e("checkAuthStatus() finished launching coroutine.") // Log that launch call completed
    }

    override fun onCleared() {
        Timber.tag("SplashVM").e("SplashViewModel onCleared()") // Log ViewModel destruction
        super.onCleared()
    }
}

