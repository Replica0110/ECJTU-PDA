package com.lonx.ecjtu.pda.service

import android.app.NotificationChannel
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
import com.lonx.ecjtu.pda.common.NetworkType
import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.CampusNetStatus.CONNECTION_ERROR
import com.lonx.ecjtu.pda.data.model.CampusNetStatus.LOGGED_IN
import com.lonx.ecjtu.pda.data.model.CampusNetStatus.NOT_CAMPUS_NET
import com.lonx.ecjtu.pda.data.model.CampusNetStatus.NOT_LOGGED_IN
import com.lonx.ecjtu.pda.data.model.CampusNetStatus.SOCKET_ERROR
import com.lonx.ecjtu.pda.data.model.CampusNetStatus.UNKNOWN_ERROR
import com.lonx.ecjtu.pda.domain.usecase.CampusNetLoginUseCase
import com.lonx.ecjtu.pda.domain.usecase.CheckCredentialsExistUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetCampusNetStatusUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetNetworkTypeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class WifiTileService : TileService(), BaseService {

    private val campusNetLoginUseCase: CampusNetLoginUseCase by inject()
    private val checkCredentialsExistUseCase: CheckCredentialsExistUseCase by inject()
    private val getNetworkTypeUseCase: GetNetworkTypeUseCase by inject()
    private val getCampusNetStatusUseCase: GetCampusNetStatusUseCase by inject()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private companion object {
        const val CHANNEL_ID = "ecjtupda_channel"
        const val CHANNEL_NAME = "ECJTUPDAChannel"
        const val CHANNEL_DESCRIPTION = "Channel for ECJTU-PDA notifications"
        const val NOTIFICATION_ID = 1
    }
    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = CHANNEL_DESCRIPTION
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    override fun onStartListening() {
        super.onStartListening()
        createNotificationChannel()
        qsTile?.updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onClick() {
        super.onClick()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        } ?: return
        // 先检查是否配置账号密码
        if (!checkCredentialsExistUseCase(checkIsp = true)) {
            sendNotification("登录失败", "请先配置账号和密码")
            return
        }


        // 异步处理网络状态和登录逻辑
        serviceScope.launch {
            val networkType = getNetworkTypeUseCase(this@WifiTileService)

            if (networkType != NetworkType.WIFI) {
                // 如果没有连接到校园WLAN，提示
                withContext(Dispatchers.Main) {
                    sendNotification("登录失败", "请连接校园网")
                }
                return@launch
            }

            // 网络状态 OK，开始检查校园网登录状态
            val status = getCampusNetStatusUseCase()
            val (title, message) = when (status) {
                LOGGED_IN -> Pair("已登录", "您已经登录校园网了")
                NOT_LOGGED_IN -> {
                    when (val result = campusNetLoginUseCase()) {
                        is PDAResult.Success -> Pair("登录成功", result.data)
                        is PDAResult.Error -> Pair("登录失败", result.message)
                    }
                }
                CONNECTION_ERROR -> Pair("连接错误", "请检查网络连接")
                SOCKET_ERROR -> Pair("连接超时", "请重试")
                UNKNOWN_ERROR -> Pair("未知错误", "可能网络故障")
                NOT_CAMPUS_NET -> Pair("非校园网", "当前网络不是校园网")
            }

            // 在主线程更新Tile状态和发送通知
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    state = Tile.STATE_INACTIVE
                    updateTile()
                }
                sendNotification(title, message)
            }
        }
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

