package com.lonx.ecjtu.pda.common.monitor

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.lonx.ecjtu.pda.data.common.LocationStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import slimber.log.e

class LocationStatusMonitor(
    private val context: Context
) {
    /**
     * 检查位置服务是否开启。
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查当前应用是否被授予位置权限。
     */
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 判断是否已申请权限但被拒绝。
     */
    private fun isPermissionDenied(): Boolean {
        return if (context is Activity) {
            !hasLocationPermission() && ActivityCompat.shouldShowRequestPermissionRationale(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            e {"Not Activity"}
            true
        }
    }

    /**
     * 创建一个流（Flow），实时监控位置服务状态和权限状态的变化。
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val locationStatus: Flow<LocationStatus> = callbackFlow {
        // 初始状态
        val initialStatus = getLocationStatus()
        channel.trySend(initialStatus)

        // 定义广播接收器监听位置服务的状态变化
        val locationProviderReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = getLocationStatus()
                channel.trySend(status)
            }
        }
        val locationProviderFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(locationProviderReceiver, locationProviderFilter,
            Context.RECEIVER_NOT_EXPORTED)

        // 当流关闭时，清理资源
        awaitClose {
            context.unregisterReceiver(locationProviderReceiver)
        }
    }.distinctUntilChanged().conflate()

    /**
     * 获取当前的位置状态，包括是否开启服务和权限状态。
     */
    private fun getLocationStatus(): LocationStatus {
        return when {
            !isLocationEnabled() -> LocationStatus.Disabled
            hasLocationPermission() -> LocationStatus.Enabled
            isPermissionDenied() -> LocationStatus.PermissionDenied
            else -> LocationStatus.Unknown
        }
    }

}
