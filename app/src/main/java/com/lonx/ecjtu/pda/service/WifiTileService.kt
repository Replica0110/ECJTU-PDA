package com.lonx.ecjtu.pda.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import com.lonx.ecjtu.pda.MainActivity
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.NetworkType
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class WifiTileService : TileService(),BaseService {
    private val service = WifiService()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val prefs:PreferencesManager by inject<PreferencesManager>()
    private companion object {
        const val CHANNEL_ID = "ecjtupad_channel"
        const val CHANNEL_NAME = "ECJTUPDAChannel"
        const val CHANNEL_DESCRIPTION = "Channel for ECJTU-PDA notifications"
        const val NOTIFICATION_ID = 1
    }
    private fun doLogin(studentid: String, password: String, theISP: Int) {
        serviceScope.launch {
            val state = service.getState()
            val (title, message) = when (state) {
                1 -> "登录失败" to "请检查网络连接"
                2 -> "登录失败" to "未知错误，请检查网络和设备状态"
                3 -> if (studentid.isEmpty() || password.isEmpty()) {
                    "账号/密码为空" to "请检查账号/密码是否填写并保存"
                } else {
                    try {
                        val result = service.login(studentid, password, theISP)
                        if (result.startsWith("E")) {
                            "登录失败" to result.substring(3)
                        } else {
                            "登录成功" to result
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        "登录失败" to "未知错误：${e.message}"
                    }
                }
                4 -> "登录成功" to "您已经登录到校园网了"
                else -> "登录失败" to "未知错误，请检查网络和设备状态"
            }
            withContext(Dispatchers.Main){
                sendNotification(title, message)
            }
        }
    }
    override fun onStartListening() {
        super.onStartListening()
        qsTile.updateTile()
    }
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
    override fun onStopListening() {
        super.onStopListening()
        qsTile.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    private fun wifiStatus(): NetworkType {
        return service.getNetworkType(this)
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val credentials=prefs.getCredentials()
        val (studentid, password, theISP) = credentials
        val currentWifiStatus = wifiStatus()

        if (currentWifiStatus == NetworkType.CELLULAR || currentWifiStatus == NetworkType.UNKNOWN) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "请连接WLAN"
            tile.updateTile()
            sendNotification("登录失败", "请先连接到校园WLAN")
            return
        }

        if (studentid.isEmpty() || password.isEmpty()) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "请配置账号"
            tile.updateTile()
            sendNotification("登录失败", "请先配置账号和密码")

            return
        }

        tile.state = Tile.STATE_ACTIVE
        tile.label = "登录中..."
        tile.updateTile()

        doLogin(studentid, password, theISP)
    }

    private fun sendNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.tile_icon)
            .setContentTitle(title)
            .setShowWhen(true)
            .setSubText("一键登录")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }
}