package com.lonx.ecjtu.pda.domain.repository

interface PreferencesRepository {

    fun getWeiXinId(): String

    fun setWeiXinId(weixinid: String)

    fun hasCredentials(checkIsp: Boolean = false): Boolean

    fun getCredentials(): Triple<String, String, Int>

    fun saveCredentials(studentId: String, studentPass: String, ispOption: Int)

    fun clearCredentials()
}