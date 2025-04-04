package com.lonx.ecjtu.pda.utils

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: Activity) {

    interface PermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }

    // 请求权限的函数
    fun requestPermission(permission: String, requestCode: Int, callback: PermissionCallback) {
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            // 如果应用之前请求过此权限但用户拒绝了请求，shouldShowRequestPermissionRationale() 会返回 true。
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                // 这里可以展示解释为何需要该权限，再次请求权限
            } else {
                // 应用程序第一次请求权限，或者用户之前拒绝了权限并且不再提示
                ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
            }
        } else {
            // 权限已经被授予
            callback.onPermissionGranted()
        }
    }

    // 处理权限请求结果的函数
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray, callback: PermissionCallback) {
        when (requestCode) {
            // 对应请求权限时定义的requestCode
            // 这里应该根据实际情况处理多个权限请求的情况
            else -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // 权限被用户授予
                    callback.onPermissionGranted()
                } else {
                    // 权限被用户拒绝
                    callback.onPermissionDenied()
                }
            }
        }
    }
}

