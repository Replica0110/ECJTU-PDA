package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.data.NavigationTarget
import com.lonx.ecjtu.pda.viewmodel.SplashViewModel
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SplashScreen(navController: NavHostController, viewModel: SplashViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.navigationEvent) {
        val event = uiState.navigationEvent

        if (event != null) {
            Timber.tag("SplashScreen").d("LaunchedEffect processing navigationEvent: $event")
            when (event) {
                NavigationTarget.LOGIN -> {
                    Timber.tag("SplashScreen").d("Navigating to LOGIN...")
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.SPLASH) { inclusive = true }
                    }
                    Timber.tag("SplashScreen").d("Navigation to LOGIN initiated.")
                }
                NavigationTarget.MAIN -> {
                    Timber.tag("SplashScreen").d("Navigating to MAIN...")
                    navController.navigate(AppRoutes.MAIN) {
                        popUpTo(AppRoutes.SPLASH) { inclusive = true }
                    }
                    Timber.tag("SplashScreen").d("Navigation to MAIN initiated.")
                }
            }

            viewModel.onNavigationComplete()
            Timber.tag("SplashScreen").d("Navigation event $event processed and reset.")

        } else {
            Timber.tag("SplashScreen").d("LaunchedEffect observed null navigationEvent.")
        }
    }


    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.7f)
                        .padding(horizontal = 30.dp)
                        .padding(bottom = 16.dp)
                        .height(4.dp)
                )

            Text(
                text = uiState.message,
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp).align(Alignment.CenterHorizontally)
            )
        }
    }
}