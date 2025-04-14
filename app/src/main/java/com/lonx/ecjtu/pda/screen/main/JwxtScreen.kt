package com.lonx.ecjtu.pda.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lonx.ecjtu.pda.data.JwxtRoute
import com.lonx.ecjtu.pda.data.MainRoute
import com.lonx.ecjtu.pda.screen.jwxt.JwxtContainerScreen
import com.lonx.ecjtu.pda.screen.jwxt.StuElectiveScreen
import com.lonx.ecjtu.pda.screen.jwxt.StuSchedulesScreen
import com.lonx.ecjtu.pda.screen.jwxt.StuScoreScreen
import com.lonx.ecjtu.pda.screen.jwxt.StuSecondCreditScreen

@Composable
fun JwxtScreen(
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    padding: PaddingValues,
    onTitleChange: (String) -> Unit,
    onAllowDrawerGestureChange: (Boolean) -> Unit,
    setNavigationIcon: (@Composable () -> Unit) -> Unit,
    onMenuClick: () -> Unit
) {
    val jwxtNavController = rememberNavController()
    val jwxtBackStackEntry by jwxtNavController.currentBackStackEntryAsState()
    val currentJwxtRouteString = jwxtBackStackEntry?.destination?.route

    val currentJwxtRoute = remember(currentJwxtRouteString) {
        JwxtRoute.find(currentJwxtRouteString)
    }

    LaunchedEffect(currentJwxtRoute) {
        onTitleChange(currentJwxtRoute?.title ?: MainRoute.Jwxt.title)
    }
    val isInJwxtSubPage = currentJwxtRoute != JwxtRoute.Menu

    LaunchedEffect(isInJwxtSubPage) {
        if (isInJwxtSubPage) {
            onAllowDrawerGestureChange(false)
            setNavigationIcon {
                IconButton(modifier = Modifier.padding(start = 20.dp), onClick = { jwxtNavController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            }
        } else {
            onAllowDrawerGestureChange(true)
            setNavigationIcon {
                IconButton(modifier = Modifier.padding(start = 20.dp), onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, "打开侧边栏")
                }
            }
        }
    }
    BackHandler(enabled = isInJwxtSubPage) {
        jwxtNavController.popBackStack()
    }

    NavHost(
        navController = jwxtNavController,
        startDestination = JwxtRoute.Menu.route,
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
    ) {
        composable(JwxtRoute.Menu.route) {
            JwxtContainerScreen(jwxtNavController = jwxtNavController)
        }
        composable(JwxtRoute.Score.route) {
            StuScoreScreen(onBack = { jwxtNavController.popBackStack() })
        }
        composable(JwxtRoute.SecondCredit.route) {
            StuSecondCreditScreen(onBack = { jwxtNavController.popBackStack() })
        }
        composable(JwxtRoute.Schedules.route) {
            StuSchedulesScreen(onBack = { jwxtNavController.popBackStack() })
        }
        (composable(JwxtRoute.ElectiveCourse.route) {
            StuElectiveScreen(onBack = { jwxtNavController.popBackStack() })
        })
    }
}