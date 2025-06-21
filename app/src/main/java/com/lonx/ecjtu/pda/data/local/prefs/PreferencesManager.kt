package com.lonx.ecjtu.pda.data.local.prefs

import android.net.Uri
import com.lonx.ecjtu.pda.data.local.prefs.PrefKeys.ISP
import com.lonx.ecjtu.pda.data.local.prefs.PrefKeys.STU_ID
import com.lonx.ecjtu.pda.data.local.prefs.PrefKeys.STU_PASSWORD
import com.lonx.ecjtu.pda.data.local.prefs.PrefKeys.WEI_XIN_ID
import com.tencent.mmkv.MMKV
import timber.log.Timber

class PreferencesManager private constructor() {
    private val kv = MMKV.defaultMMKV()
    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager().also { instance = it }
            }
        }
    }
    fun getString(key: String, defaultValue: String): String {
        return kv.decodeString(key, defaultValue) ?: defaultValue
    }
    fun getInt(key: String, defaultValue: Int): Int {
        return kv.decodeInt(key, defaultValue)
    }
    fun setPrefs(key: String, value: Any) {
        when (value) {
            is String -> kv.encode(key, value)
            is Int -> kv.encode(key, value)
            is Boolean -> kv.encode(key, value)
            is Float -> kv.encode(key, value)
            is Long -> kv.encode(key, value)
            is ByteArray -> kv.encode(key, value)
            else -> throw IllegalArgumentException("Unsupported type: ${value::class.java.name}")
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return kv.decodeBool(key, defaultValue)
    }
    fun setBoolean(key: String, value: Boolean) {
        kv.encode(key, value)
    }
    /** 检查是否已经保存学号和密码*/
    fun hasCredentials(checkIsp: Boolean = false): Boolean {
        if (checkIsp) {
            return kv.contains(STU_ID) && kv.contains(STU_PASSWORD) && kv.contains(ISP)
        }
        return kv.contains(STU_ID) && kv.contains(STU_PASSWORD)
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
            kv.encode(WEI_XIN_ID, extractedId)
        } else {
            Timber.e("Failed to extract ID from URL: $weixinidInput")
        }
    }
    fun getWeiXinId(): String {
        return kv.decodeString(WEI_XIN_ID, "") ?: ""
    }
    /**保存账号及密码 */
    fun saveCredentials(studentId: String?=null, password: String?=null, isp: Int? = null) {
        studentId?.let {
            kv.encode(STU_ID, studentId)
        }
        password?.let {
            kv.encode(STU_PASSWORD, password)
        }
        isp?.let {
            kv.encode(ISP, it)
        }
    }
    fun saveIsp(isp: Int) {
        kv.encode(ISP, isp)
    }
    fun getIsp(): Int {
        return kv.decodeInt(ISP, 1)
    }
    /*清空账号及密码*/
    fun clearCredentials() {
        kv.remove(STU_ID)
        kv.remove(STU_PASSWORD)
        kv.remove(ISP)
    }
    fun getCredentials(): Triple<String, String, Int> {
        val studentId = kv.decodeString(STU_ID, "")?:""
        val password = kv.decodeString(STU_PASSWORD, "")?:""
        val ispId = kv.decodeInt(ISP, 1)

        Timber.e("getCredentials - Retrieved: ID='$studentId', Pass='***', ISP=$ispId")

        return Triple(studentId, password, ispId)
    }


}