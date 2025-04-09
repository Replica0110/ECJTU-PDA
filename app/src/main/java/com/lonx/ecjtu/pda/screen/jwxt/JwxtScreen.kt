package com.lonx.ecjtu.pda.screen.jwxt

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
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior

object JwxtDestinations {

    const val EXAM_INFO_ROUTE = "jwxt_exam_info"
    const val COURSE_SCHEDULE_ROUTE = "jwxt_my_course"
    const val MENU_ROUTE = "jwxt_menu"
    const val SCORE_ROUTE = "jwxt_score"
    const val SECOND_CREDIT_ROUTE = "jwxt_second_credit"
}
@Composable
fun JwxtScreen(
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    padding: PaddingValues,
    onTitleChange: (String) -> Unit,
    setNavigationIcon: (@Composable () -> Unit) -> Unit,
    onMenuClick: () -> Unit
) {
    val jwxtNavController = rememberNavController()
    val jwxtBackStackEntry by jwxtNavController.currentBackStackEntryAsState()
    val currentJwxtRoute = jwxtBackStackEntry?.destination?.route

    val currentJwxtScreenTitle = remember(currentJwxtRoute) {
        when (currentJwxtRoute) {
            JwxtDestinations.MENU_ROUTE -> "教务系统"
            JwxtDestinations.SCORE_ROUTE -> "我的成绩"
            JwxtDestinations.COURSE_SCHEDULE_ROUTE -> "我的课表"
            JwxtDestinations.EXAM_INFO_ROUTE -> "考试安排"
            JwxtDestinations.SECOND_CREDIT_ROUTE -> "素拓学分"
            else -> "教务系统"
        }
    }
    LaunchedEffect(currentJwxtScreenTitle) {
        onTitleChange(currentJwxtScreenTitle)
    }
    val isInJwxtSubPage = jwxtNavController.previousBackStackEntry != null

    LaunchedEffect(isInJwxtSubPage) {
        if (isInJwxtSubPage) {
            setNavigationIcon {
                IconButton(modifier = Modifier.padding(start = 20.dp), onClick = { jwxtNavController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            }
        } else {
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
        startDestination = JwxtDestinations.MENU_ROUTE,
        modifier = Modifier.padding(padding).fillMaxSize(),
    ) {
        composable(JwxtDestinations.MENU_ROUTE) {
            JwxtMenuScreen(jwxtNavController = jwxtNavController)
        }
        composable(JwxtDestinations.SCORE_ROUTE) {
            StuScoreScreen(onBack = { jwxtNavController.popBackStack() })
        }
        composable(JwxtDestinations.SECOND_CREDIT_ROUTE) {
            StuSecondCreditScreen(onBack = { jwxtNavController.popBackStack() })
        }
    }
}