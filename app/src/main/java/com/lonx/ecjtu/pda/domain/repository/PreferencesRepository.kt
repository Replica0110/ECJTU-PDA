package com.lonx.ecjtu.pda.domain.repository

interface PreferencesRepository {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun setBoolean(key: String, value: Boolean)

    fun getWeiXinId(): String

    fun setWeiXinId(weixinid: String)

    fun hasCredentials(checkIsp: Boolean = false): Boolean

    fun getCredentials(): Triple<String?, String?, Int?>

    fun saveCredentials(studentId: String?=null, studentPass: String?=null, ispOption: Int?=null)

    fun clearCredentials()
}