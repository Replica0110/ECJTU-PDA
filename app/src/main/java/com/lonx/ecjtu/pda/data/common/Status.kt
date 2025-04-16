package com.lonx.ecjtu.pda.data.common

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class NavigationTarget { LOGIN, MAIN }





enum class CardType {
    PROFILE,
    WIFI,
    CLASSES,
    SCORE,
    LOGIN,
    TEST,//测试字段
}


val availableIsp = listOf(
    IspOption(1, "中国移动"),
    IspOption(2, "中国电信"),
    IspOption(3, "中国联通")
)

@Parcelize
data class IspOption(
    val id: Int,
    val name: String
) : Parcelable


sealed class LocationStatus {
    data object Disabled : LocationStatus()
    data object Enabled : LocationStatus()
    data object PermissionDenied : LocationStatus()
    data object Unknown : LocationStatus()
}

sealed class WifiStatus {
    data object Connected : WifiStatus()
    data object Disconnected : WifiStatus()
    data object Disabled : WifiStatus()
    data object Unknown : WifiStatus()
    data object Enabled : WifiStatus()
}