package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.data.NavigationTarget
import com.lonx.ecjtu.pda.viewmodel.SplashViewModel
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@Composable
fun SplashScreen(navController: NavHostController, viewModel: SplashViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigationEvent) {
        Timber.tag("SplashScreen").d("LaunchedEffect checking navigationEvent: ${uiState.navigationEvent}")
        when (uiState.navigationEvent) {
            NavigationTarget.LOGIN -> {
                Timber.tag("SplashScreen").d("Navigating to LOGIN...")
                navController.navigate(AppRoutes.LOGIN) {
                    popUpTo(AppRoutes.SPLASH) { inclusive = true }
                }
                Timber.tag("SplashScreen").d("Navigation to LOGIN complete.")
            }
            NavigationTarget.MAIN -> {
                Timber.tag("SplashScreen").d("Navigating to MAIN...")
                navController.navigate(AppRoutes.MAIN) {
                    popUpTo(AppRoutes.SPLASH) { inclusive = true }
                }
                Timber.tag("SplashScreen").d("Navigation to MAIN complete.")
            }
            null -> {
                Timber.tag("SplashScreen").d("navigationEvent is null, doing nothing.")
                // Do nothing, wait for the event
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (uiState.isLoading) {
            // Or just a logo, etc.
            Text("华交校园网工具")
        }
    }
}