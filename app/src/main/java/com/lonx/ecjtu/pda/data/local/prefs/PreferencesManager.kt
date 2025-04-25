package com.lonx.ecjtu.pda.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.lonx.ecjtu.pda.data.local.prefs.PrefKeys.ISP
import com.lonx.ecjtu.pda.data.local.prefs.PrefKeys.STU_ID
import com.lonx.ecjtu.pda.data.local.prefs.PrefKeys.STU_PASSWORD
import com.lonx.ecjtu.pda.data.local.prefs.PrefKeys.WEI_XIN_ID
import timber.log.Timber

class PreferencesManager private constructor(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }
    fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
    fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }

    fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }
    fun getInt(key: String, defaultValue: Int): Int {
        return preferences.getInt(key, defaultValue)
    }
    /** 检查是否已经保存学号和密码*/
    fun hasCredentials(checkIsp: Boolean = false): Boolean {
        if (checkIsp) {
            return preferences.contains(STU_ID) && preferences.contains(STU_PASSWORD) && preferences.contains(ISP)
        }
        return preferences.contains(STU_ID) && preferences.contains(STU_PASSWORD)
    }

    private fun edit(function: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply {
            function()
        }.apply()
    }
    fun setWeiXinId(weixinidInput: String) {
        if (weixinidInput.isBlank()) {
            return
        }

        val targetPrefix = "https://jwxt.ecjtu.edu.cn/weixin/"
        var extractedId: String? = null

        if (weixinidInput.startsWith(targetPrefix)) {
            try {
                val uri = Uri.parse(weixinidInput)
                extractedId = uri.getQueryParameter("weiXinID")
            } catch (e: Exception) {
                Timber.e("Failed to parse URL: ${e.message}")
            }
        } else {
            extractedId = weixinidInput
        }

        if (!extractedId.isNullOrBlank()) {
            edit {
                putString(WEI_XIN_ID, extractedId)
            }
        } else {
            Timber.e("Failed to extract ID from URL: $weixinidInput")
        }
    }
    fun getWeiXinId(): String {
        return preferences.getString(WEI_XIN_ID, "") ?: ""
    }
    /**保存账号及密码 */
    fun saveCredentials(studentId: String, password: String, isp: Int? = null) {
        edit {
            putString(STU_ID, studentId)
            putString(STU_PASSWORD, password)
            if (isp != null) {
                putInt(ISP, isp)
            } else {
                remove(ISP)
            }
        }
    }
    /*清空账号及密码*/
    fun clearCredentials() {
        edit {
            remove(STU_ID)
            remove(STU_PASSWORD)
            remove(ISP)
        }
    }
    fun getCredentials(): Triple<String, String, Int> {
        val studentId = preferences.getString(STU_ID, "") ?: ""
        val password = preferences.getString(STU_PASSWORD, "") ?: ""
        val ispId = preferences.getInt(ISP, 1)

        Timber.e("getCredentials - Retrieved: ID='$studentId', Pass='***', ISP=$ispId")

        return Triple(studentId, password, ispId)
    }
}