package com.lonx.ecjtu.pda

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.screen.top.LoginScreen
import com.lonx.ecjtu.pda.screen.top.MainScreen
import com.lonx.ecjtu.pda.screen.top.SplashScreen
import com.lonx.ecjtu.pda.utils.PreferencesManager
import org.koin.android.ext.android.inject
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    private val prefs:PreferencesManager by inject<PreferencesManager>()
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseIntent(intent)
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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
                        val jwxtNavController = rememberNavController()
                        MainScreen(
                            topLevelNavController = topLevelNavController,
                            internalNavController = mainInternalNavController,
                            jwxtNavController = jwxtNavController
                        )
                    }

                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseIntent(intent)
    }
    private fun parseIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            uri?.let {
                val weixinID = it.getQueryParameter("weiXinID")
                weixinID?.let { id ->
                    prefs.setWeiXinId(id)
                }
            }
        }
    }
}
