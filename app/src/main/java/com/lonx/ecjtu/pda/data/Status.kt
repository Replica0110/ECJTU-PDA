package com.lonx.ecjtu.pda.data

import android.os.Parcelable
import androidx.annotation.DrawableRes
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.base.BaseUiState
import kotlinx.parcelize.Parcelize

enum class NavigationTarget { LOGIN, MAIN }

data class StuInfoUiState(
    val isLoading: Boolean = false,
    val studentInfo: StudentInfo? = null,
    val error: String? = null // 用于显示错误消息
): BaseUiState

data class SplashUiState(
    val isLoading: Boolean = true,
    val navigationEvent: NavigationTarget? = null
): BaseUiState
enum class CardType {
    PROFILE,
    WIFI,
    CLASSES,
    SCORE,
    LOGIN,
    TEST,//测试字段
}
data class HomeUiState(
    val isLoading: Boolean = false,
    val card: CardType? = null,
    val error: String? = null
): BaseUiState
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
data class LoginUiState(
    val studentId: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigationEvent: NavigationTarget? = null,
    val ispOptions: List<IspOption> = availableIsp,
    val selectedIspId: Int = 1
): BaseUiState
data class WifiUiState(
    @DrawableRes val wifiStatusIconRes: Int = R.drawable.ic_wifi_disabled,
    val wifiStatusText: String = "WLAN 未启用",
    val ssid: String = "当前无连接",
    val isLocationServiceEnabled: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isLoadingIn: Boolean = false,
    val isLoadingOut: Boolean = false
):BaseUiState

data class SettingUiState(
    val studentId: String = "",
    val password: String = "",
    val ispSelected: IspOption = IspOption(1, "中国移动"),
    val isLoading: Boolean = false,
    val error: String? = null
): BaseUiState

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