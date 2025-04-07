package com.lonx.ecjtu.pda.screen.jwxt

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior

object JwxtDestinations {
    const val MENU_ROUTE = "jwxt_menu"
    const val SCORE_ROUTE = "jwxt_score"
    const val SECOND_CREDIT_ROUTE = "jwxt_second_credit"
}
@Composable
fun JwxtScreen(
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    jwxtNavController: NavHostController,
    scrollBehavior: UpdatableScrollBehavior,
    padding: PaddingValues,
) {


    NavHost(
        navController = jwxtNavController,
        startDestination = JwxtDestinations.MENU_ROUTE,
        modifier = Modifier.padding(padding) ,
    ) {
        composable(JwxtDestinations.MENU_ROUTE) {
            JwxtMenuScreen(
                jwxtNavController = jwxtNavController,
                scrollBehavior = scrollBehavior
            )
        }

        composable(JwxtDestinations.SCORE_ROUTE) {
            StuScoreScreen(
                onBack = {jwxtNavController.popBackStack()},
                scrollBehavior = scrollBehavior
            )
        }
        // TODO:素拓学分界面
        composable(JwxtDestinations.SECOND_CREDIT_ROUTE) {  }
    }
}