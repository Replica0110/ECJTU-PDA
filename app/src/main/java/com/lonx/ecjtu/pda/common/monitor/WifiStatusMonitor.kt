package com.lonx.ecjtu.pda.common.monitor

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.lonx.ecjtu.pda.data.common.WifiStatus
import com.lonx.ecjtu.pda.extension.isWifiConnected
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

class WifiStatusMonitor(
    private val wifiManager: WifiManager,
    private val connectivityManager: ConnectivityManager,
    private val context: Context
) {
    // 创建网络请求对象，指定只关注 Wi-Fi 连接
    private val networkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    /**
     * 根据 Wi-Fi 和连接状态判断当前的 Wi-Fi 状态。
     */
    private fun getWifiStatus(wifiManager: WifiManager, connectivityManager: ConnectivityManager): WifiStatus =
        when {
            !wifiManager.isWifiEnabled -> WifiStatus.Disabled // 如果 Wi-Fi 被禁用，返回禁用状态
            connectivityManager.isWifiConnected == true -> WifiStatus.Connected // 如果 Wi-Fi 已连接，返回连接状态
            else -> WifiStatus.Disconnected // 否则返回断开状态
        }

    /**
     * 获取当前连接的 Wi-Fi 网络的 SSID（如果可用）。
     * 需要位置权限。
     */
    fun getSSID(context: Context): String? {
        // 检查是否具有位置权限
        if (!hasLocationPermission(context)) {
//            throw SecurityException("Location permission not granted") // 没有权限时抛出异常
            return "未授予位置权限"
        }

        val wifiInfo = wifiManager.connectionInfo
        // 如果 Wi-Fi 已连接，返回 SSID，移除双引号
        return if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
            wifiInfo.ssid?.replace("\"", "") // 移除双引号
        } else {
            null // 如果未连接，则返回 null
        }
    }

    /**
     * 检查应用是否有访问 Wi-Fi SSID 所需的位置权限。
     */
    private fun hasLocationPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 生成一个流（Flow），实时发出当前 Wi-Fi 状态并在状态变化时更新。
     */

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val wifiStatus: Flow<WifiStatus> = callbackFlow {
        // 发送初始 Wi-Fi 状态
        val initialStatus = getWifiStatus(wifiManager, connectivityManager)
        channel.trySend(initialStatus)

        // 定义网络回调
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val status = WifiStatus.Connected
                channel.trySend(status)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (network == connectivityManager.activeNetwork) {
                    val status = WifiStatus.Connected
                    channel.trySend(status)
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network == connectivityManager.activeNetwork) {
                    val status = WifiStatus.Connected
                    channel.trySend(status)
                }
            }

            override fun onUnavailable() {
                val status = WifiStatus.Disconnected
                channel.trySend(status)
            }

            override fun onLost(network: Network) {
                val status = wifiManager.getNoConnectionPresentStatus()
                channel.trySend(status)
            }
        }

        // 注册网络回调
        connectivityManager.registerNetworkCallback(networkRequest, callback)


        // 定义广播接收器监听 Wi-Fi 开关状态
        val wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                        val status = when (wifiState) {
                            WifiManager.WIFI_STATE_ENABLED -> {
                                // 重新检查当前的 WiFi 连接状态
                                getWifiStatus(wifiManager, connectivityManager)
                            }
                            WifiManager.WIFI_STATE_DISABLED -> WifiStatus.Disabled
                            else -> WifiStatus.Unknown
                        }
                        channel.trySend(status)
                    }
                }
            }
        }
        val wifiStateIntentFilter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        context.registerReceiver(wifiStateReceiver, wifiStateIntentFilter,
            Context.RECEIVER_NOT_EXPORTED)

        // 当流收集器关闭时，清理资源
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback) // 取消注册网络回调
            context.unregisterReceiver(wifiStateReceiver)
        }
    }
        .distinctUntilChanged()
        .conflate()

    /**
     * 扩展函数，用于判断当没有连接时 Wi-Fi 的状态。
     */
    private fun WifiManager.getNoConnectionPresentStatus(): WifiStatus =
        if (isWifiEnabled) WifiStatus.Disconnected else WifiStatus.Disabled
}