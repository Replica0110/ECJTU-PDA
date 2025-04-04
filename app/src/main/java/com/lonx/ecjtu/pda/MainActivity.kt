package com.lonx.ecjtu.pda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.screen.LoginScreen
import com.lonx.ecjtu.pda.screen.MainScreen
import com.lonx.ecjtu.pda.screen.SplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val topLevelNavController = rememberNavController()
                NavHost(
                    navController = topLevelNavController,
                    startDestination = AppRoutes.SPLASH
                ) {
                    composable(AppRoutes.SPLASH) {
                        SplashScreen(navController = topLevelNavController)
                    }

                    composable(AppRoutes.LOGIN) {
                        LoginScreen(
                            navController = topLevelNavController,
                            onLoginSuccess = {
                                topLevelNavController.navigate(AppRoutes.MAIN) {
                                    popUpTo(AppRoutes.LOGIN) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(AppRoutes.MAIN) {
                        val mainInternalNavController = rememberNavController()
                        MainScreen(
                            topLevelNavController = topLevelNavController,
                            internalNavController = mainInternalNavController
                        )
                    }

                }
            }
        }
    }
}
