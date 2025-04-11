package com.lonx.ecjtu.pda.data

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.lonx.ecjtu.pda.R

sealed class TopLevelRoute(val route: String) {
    data object Splash : TopLevelRoute("splash")
    data object Login : TopLevelRoute("login")
    data object Main : TopLevelRoute("main")

    companion object {
        fun find(route: String?): TopLevelRoute? {
            return listOf(Splash, Login, Main).find { it.route == route }
        }
    }
}

sealed class MainRoute(
    val route: String,
    val title: String,
    val icon: ImageVector? = null,
    @DrawableRes val painterResId: Int? = null,
    val isTopLevelDestination: Boolean = true
) {
    data object Home : MainRoute(
        route = "home",
        title = "主页",
        icon = Icons.Outlined.Home,
        painterResId = null
    )

    data object Jwxt : MainRoute(
        route = "jwxt",
        title = "教务系统",
        icon = Icons.Outlined.AccountBox,
        painterResId = null
    )

    data object Wifi : MainRoute(
        route = "wifi",
        title = "校园网",
        icon = null,
        painterResId = R.drawable.ic_menu_wifi
    )

    data object Settings : MainRoute(
        route = "settings",
        title = "设置",
        icon = Icons.Outlined.Settings,
        painterResId = null
    )

    data object Profile : MainRoute(
        route = "profile",
        title = "个人信息",
        icon = Icons.Outlined.AccountCircle,
        painterResId = null
    )

    companion object {
        val drawerItems = listOf(Profile, Home, Jwxt, Wifi, Settings)

        fun find(route: String?): MainRoute? {
            return listOf(Home, Jwxt, Wifi, Settings, Profile).find { it.route == route }
        }

        val topLevelRoutesForExit = setOf(Home, Profile, Wifi, Settings, Jwxt)
    }
}
sealed class JwxtRoute(
    val route: String,
    val title: String
) {
    data object Menu : JwxtRoute("jwxt_menu", MainRoute.Jwxt.title)
    data object Score : JwxtRoute("jwxt_score", "我的成绩")
    data object Schedules : JwxtRoute("jwxt_course", "我的课表")
    data object ExamInfo : JwxtRoute("jwxt_exam", "考试安排")
    data object SecondCredit : JwxtRoute("jwxt_second_credit", "素拓学分")


    companion object {
        fun find(route: String?): JwxtRoute? {
            return listOf(Menu, Score, Schedules, ExamInfo, SecondCredit)
                .find { it.route == route }
        }
    }
}
