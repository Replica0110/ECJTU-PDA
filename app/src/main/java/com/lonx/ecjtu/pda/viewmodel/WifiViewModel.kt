package com.lonx.ecjtu.pda.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.base.BaseUiState
import com.lonx.ecjtu.pda.common.monitor.LocationStatusMonitor
import com.lonx.ecjtu.pda.common.monitor.WifiStatusMonitor
import com.lonx.ecjtu.pda.data.common.LocationStatus
import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.common.WifiStatus
import com.lonx.ecjtu.pda.domain.usecase.CampusNetLoginUseCase
import com.lonx.ecjtu.pda.domain.usecase.CampusNetLogoutUseCase
import com.lonx.ecjtu.pda.domain.usecase.CheckCredentialsExistUseCase
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


sealed class WifiUiEvent {
    data object NavigateToWifiSettings : WifiUiEvent()
    data object NavigateToLocationSettings : WifiUiEvent()
    data object NavigateToAppSettings : WifiUiEvent()
    data object RequestLocationPermission : WifiUiEvent()
    data class ShowInfoDialog(val title: String, val message: String) : WifiUiEvent()
    data object ShowLocationEnablePromptDialog : WifiUiEvent()
    data object ShowPermissionDeniedAppSettingsPromptDialog : WifiUiEvent()
}
data class WifiUiState(
    @DrawableRes val wifiStatusIconRes: Int = R.drawable.ic_wifi_disabled,
    val wifiStatusText: String = "WLAN 未启用",
    val ssid: String = "当前无连接",
    val isLocationServiceEnabled: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isLoadingIn: Boolean = false,
    val isLoadingOut: Boolean = false
): BaseUiState

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WifiViewModel(
    private val wifiStatusMonitor: WifiStatusMonitor,
    private val locationStatusMonitor: LocationStatusMonitor,
    private val campusNetLoginUseCase: CampusNetLoginUseCase,
    private val campusNetLogoutUseCase: CampusNetLogoutUseCase,
    private val checkCredentialsExistUseCase: CheckCredentialsExistUseCase,
    private val applicationContext: Context
) : ViewModel() {


    private val _uiState = MutableStateFlow(WifiUiState())
    val uiState: StateFlow<WifiUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<WifiUiEvent>()
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
            _uiEvent.emit(WifiUiEvent.NavigateToWifiSettings)
        }
    }

    fun onCheckPermissionsClicked() {
        viewModelScope.launch {
            val locationStatus = locationStatusMonitor.locationStatus.first()
            if (locationStatus == LocationStatus.Disabled) {
                _uiEvent.emit(WifiUiEvent.ShowLocationEnablePromptDialog)
            } else {
                if (!hasLocationPermission()) {
                    _uiEvent.emit(WifiUiEvent.RequestLocationPermission)
                } else {
                    _uiEvent.emit(WifiUiEvent.ShowInfoDialog("权限状态", "位置权限已授予"))
                    checkPermission(forceUpdate = true)
                }
            }
        }
    }

    fun navigateToLocationSettings() {
        viewModelScope.launch {
            _uiEvent.emit(WifiUiEvent.NavigateToLocationSettings)
        }
    }
    // --- 当权限请求结果返回时调用 ---
    fun onPermissionResult(granted: Boolean) {
        checkPermission(forceUpdate = granted)

        if (!granted) {
            viewModelScope.launch {
                _uiEvent.emit(WifiUiEvent.ShowPermissionDeniedAppSettingsPromptDialog)
            }
        }
    }
    fun refreshState() {
        checkPermission(forceUpdate = true)
    }
    fun showPermissionExplain() {
        viewModelScope.launch {
            _uiEvent.emit(WifiUiEvent.ShowInfoDialog("需要位置权限", "应用需要位置权限以获取精确的WiFi信息 (SSID)。请授予该权限。"))

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
            _uiEvent.emit(WifiUiEvent.ShowPermissionDeniedAppSettingsPromptDialog)
        }
    }


    fun navigateToAppSettings() {
        viewModelScope.launch {
            _uiEvent.emit(WifiUiEvent.NavigateToAppSettings)
        }
    }


    fun onLoginClicked() {
        val currentState = _uiState.value
        if (currentState.isLoadingIn) return

        if (currentState.wifiStatusIconRes != R.drawable.ic_wifi_connected) {
            viewModelScope.launch {
                _uiEvent.emit(WifiUiEvent.ShowInfoDialog("登录失败", "请先连接校园网"))
            }
            return
        }

        d { "校园网登录中" }

        if (!checkCredentialsExistUseCase(checkIsp = true)) {
            viewModelScope.launch {
                _uiEvent.emit(WifiUiEvent.ShowInfoDialog("登录信息", "请先设置学号和密码"))
            }
            return
        }

        _uiState.update { it.copy(isLoadingIn = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = campusNetLoginUseCase()
                val title: String
                val message: String
                when (result) {
                    is PDAResult.Success -> {
                        title="登录成功"
                        message = result.data

                    }
                    is PDAResult.Error -> {
                       title= "登录失败"
                        message = result.message
                    }
                }
                _uiEvent.emit(WifiUiEvent.ShowInfoDialog(title, message))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(WifiUiEvent.ShowInfoDialog("登录失败", "发生错误: ${e.message}"))
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
                _uiEvent.emit(WifiUiEvent.ShowInfoDialog("注销失败", "请先连接校园网"))
            }
            return
        }

        d { "Login out" }
        _uiState.update { it.copy(isLoadingOut = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = campusNetLogoutUseCase()
                val title: String
                val message: String
                when (result) {
                    is PDAResult.Success -> {
                        title="注销成功"
                        message = result.data
                    }
                    is PDAResult.Error -> {
                        title="注销失败"
                        message = result.message
                    }
                }

                _uiEvent.emit(WifiUiEvent.ShowInfoDialog(title, message))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(WifiUiEvent.ShowInfoDialog("注销失败", "发生错误: ${e.message}"))
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