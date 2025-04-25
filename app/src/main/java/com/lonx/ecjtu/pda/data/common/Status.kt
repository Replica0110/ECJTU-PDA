package com.lonx.ecjtu.pda.data.common

enum class NavigationTarget { LOGIN, MAIN }





enum class CardType {
    PROFILE,
    WIFI,
    CLASSES,
    SCORE,
    LOGIN,
    TEST,//测试字段
}





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