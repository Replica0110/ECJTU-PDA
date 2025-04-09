package com.lonx.ecjtu.pda

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.lonx.ecjtu.pda.data.TopLevelRoute
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
            MiuixTheme {
                val systemUiController = rememberSystemUiController()

                val statusBarColor = MiuixTheme.colorScheme.background

                DisposableEffect(systemUiController, statusBarColor) {
                    systemUiController.setStatusBarColor(
                        color = statusBarColor
                    )

                    onDispose { }
                }
                val topLevelNavController = rememberNavController()
                NavHost(
                    navController = topLevelNavController,
                    startDestination = TopLevelRoute.Splash.route
                ) {
                    composable(TopLevelRoute.Splash.route) {
                        SplashScreen(navController = topLevelNavController)
                    }

                    composable(TopLevelRoute.Login.route) {
                        LoginScreen(
                            navController = topLevelNavController,
                            onLoginSuccess = {
                                topLevelNavController.navigate(TopLevelRoute.Main.route) {
                                    popUpTo(TopLevelRoute.Login.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(TopLevelRoute.Main.route) {
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
