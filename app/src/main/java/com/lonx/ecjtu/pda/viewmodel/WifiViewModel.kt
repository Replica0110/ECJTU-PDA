package com.lonx.ecjtu.pda.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.base.BaseViewModel
import com.lonx.ecjtu.pda.data.LocationStatus
import com.lonx.ecjtu.pda.data.WifiStatus
import com.lonx.ecjtu.pda.data.WifiUiState
import com.lonx.ecjtu.pda.network.WifiStatusMonitor
import com.lonx.ecjtu.pda.service.WifiService
import com.lonx.ecjtu.pda.utils.LocationStatusMonitor
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import slimber.log.d


sealed class UiEvent {
    data object NavigateToWifiSettings : UiEvent()
    data object NavigateToLocationSettings : UiEvent()
    data object NavigateToAppSettings : UiEvent()
    data object RequestLocationPermission : UiEvent()
    data class ShowInfoDialog(val title: String, val message: String) : UiEvent()
    data object ShowLocationEnablePromptDialog : UiEvent()
    data object ShowPermissionDeniedAppSettingsPromptDialog : UiEvent()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WifiViewModel(
    private val wifiStatusMonitor: WifiStatusMonitor,
    private val locationStatusMonitor: LocationStatusMonitor,
    override val prefs: PreferencesManager,
    private val applicationContext: Context
) : ViewModel(), BaseViewModel {

    override val service = WifiService()

    private val _uiState = MutableStateFlow(WifiUiState())
    override val uiState: StateFlow<WifiUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        observeStatuses()
        updateLocationPermissionState()
    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun observeStatuses() {
        viewModelScope.launch {
            combine(
                wifiStatusMonitor.wifiStatus,
                locationStatusMonitor.locationStatus
            ) { wifiStatus, locationStatus ->
                Pair(wifiStatus, locationStatus)
            }.collectLatest { (wifiStatus, locationStatus) ->
                updateUiState(wifiStatus, locationStatus)
                updateLocationPermissionState()
            }
        }
    }

    private fun updateUiState(wifiStatus: WifiStatus, locationStatus: LocationStatus) {
        var currentSsid = "当前无连接"
        var currentWifiStatusText = "未知状态"
        var currentWifiIconRes = R.drawable.ic_wifi_disabled
        var currentLocationEnabled = false
        var ssidMessageOverride: String? = null
        val currentHasPermission = _uiState.value.hasLocationPermission

        when (wifiStatus) {
            WifiStatus.Disabled -> {
                currentWifiIconRes = R.drawable.ic_wifi_disabled
                currentWifiStatusText = "WLAN 未启用"
                currentSsid = "当前无连接"
            }
            WifiStatus.Disconnected, WifiStatus.Enabled -> {
                currentWifiIconRes = R.drawable.ic_wifi_disconnected
                currentWifiStatusText = "未连接 WiFi"
                currentSsid = "当前无连接"
            }
            WifiStatus.Connected -> {
                currentWifiStatusText = "已连接 WiFi"
                currentWifiIconRes = R.drawable.ic_wifi_connected
                currentSsid = if (currentHasPermission) {
                    wifiStatusMonitor.getSSID(applicationContext) ?: "获取网络SSID失败"
                } else {
                    "需要位置权限获取SSID"
                }
            }
            WifiStatus.Unknown -> {
                currentWifiIconRes = R.drawable.ic_wifi_disabled
                currentWifiStatusText = "未知状态"
                currentSsid = "当前无连接"
            }
        }

        when (locationStatus) {
            LocationStatus.Disabled -> {
                currentLocationEnabled = false
                ssidMessageOverride = "位置信息未开启, 点击检查"
            }
            LocationStatus.Enabled -> {
                currentLocationEnabled = true
                if (!currentHasPermission) {
                    ssidMessageOverride = "未授予位置权限, 点击检查"
                }
            }
            LocationStatus.PermissionDenied -> {
                currentLocationEnabled = true
                if (!currentHasPermission) {
                    ssidMessageOverride = "未授予位置权限, 点击检查"
                }
            }
            LocationStatus.Unknown -> {
                currentLocationEnabled = false
                ssidMessageOverride = "位置状态未知"
            }
        }

        _uiState.update { currentState ->
            currentState.copy(
                wifiStatusIconRes = currentWifiIconRes,
                wifiStatusText = currentWifiStatusText,
                ssid = ssidMessageOverride ?: currentSsid,
                isLocationServiceEnabled = currentLocationEnabled,
                hasLocationPermission = currentHasPermission
            )
        }
    }


    fun onOpenWifiSettingsClicked() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.NavigateToWifiSettings)
        }
    }

    fun onCheckPermissionsClicked() {
        viewModelScope.launch {
            val locationStatus = locationStatusMonitor.locationStatus.first()
            if (locationStatus == LocationStatus.Disabled) {
                _uiEvent.emit(UiEvent.ShowLocationEnablePromptDialog)
            } else {
                if (!hasLocationPermission()) {
                    _uiEvent.emit(UiEvent.RequestLocationPermission)
                } else {
                    _uiEvent.emit(UiEvent.ShowInfoDialog("权限状态", "位置权限已授予"))
                    checkPermission(forceUpdate = true)
                }
            }
        }
    }

    fun navigateToLocationSettings() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.NavigateToLocationSettings)
        }
    }
    // --- 当权限请求结果返回时调用 ---
    fun onPermissionResult(granted: Boolean) {
        checkPermission(forceUpdate = granted)

        if (!granted) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowPermissionDeniedAppSettingsPromptDialog)
            }
        }
    }
    fun refreshState() {
        checkPermission(forceUpdate = true)
    }
    fun showPermissionExplain() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowInfoDialog("需要位置权限", "应用需要位置权限以获取精确的WiFi信息 (SSID)。请授予该权限。"))

        }
    }
    private fun checkPermission(forceUpdate: Boolean = false) {
        val hasPermission = hasLocationPermission()
        val permissionChanged = _uiState.value.hasLocationPermission != hasPermission

        _uiState.update { it.copy(hasLocationPermission = hasPermission) }

        if (permissionChanged || forceUpdate) {
            viewModelScope.launch {
                val currentWifiStatus = wifiStatusMonitor.wifiStatus.first()
                val currentLocationStatus = locationStatusMonitor.locationStatus.first()
                updateUiState(currentWifiStatus, currentLocationStatus)
            }
        }
    }
    fun navigateToAppSettingsForPermission() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowPermissionDeniedAppSettingsPromptDialog)
        }
    }


    fun navigateToAppSettings() {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.NavigateToAppSettings)
        }
    }


    fun onLoginClicked() {
        val currentState = _uiState.value
        if (currentState.isLoadingIn) return

        if (currentState.wifiStatusIconRes != R.drawable.ic_wifi_connected) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowInfoDialog("登录失败", "请先连接校园网"))
            }
            return
        }

        d { "校园网登录中" }
        val stuId = prefs.getString("student_id", "")
        val stuPwd = prefs.getString("password", "")
        val isp = prefs.getInt("isp", 1)

        if (stuId.isEmpty() || stuPwd.isEmpty()) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowInfoDialog("登录信息", "请先设置学号和密码"))
            }
            return
        }

        _uiState.update { it.copy(isLoadingIn = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var result = service.login(stuId, stuPwd, isp)
                val title: String
                if (result.startsWith("E")) {
                    title = "登录失败"
                    result = result.substring(2).trim()
                } else {
                    title = "登录成功"
                }
                _uiEvent.emit(UiEvent.ShowInfoDialog(title, result))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowInfoDialog("登录失败", "发生错误: ${e.message}"))
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingIn = false) }
                }
            }
        }
    }

    fun onLogoutClicked() {
        val currentState = _uiState.value
        if (currentState.isLoadingOut) return

        if (currentState.wifiStatusIconRes != R.drawable.ic_wifi_connected) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowInfoDialog("注销失败", "请先连接校园网"))
            }
            return
        }

        d { "Login out" }
        _uiState.update { it.copy(isLoadingOut = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var result = service.loginOut()
                var title = ""
                if (result.startsWith("E")) {
                    title = "注销失败"
                    result = result.substring(2).trim()
                } else {
                    title = "注销成功"
                }
                _uiEvent.emit(UiEvent.ShowInfoDialog(title, result))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiEvent.ShowInfoDialog("注销失败", "发生错误: ${e.message}"))
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingOut = false) }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateLocationPermissionState() {
        _uiState.update { it.copy(hasLocationPermission = hasLocationPermission()) }
    }


}