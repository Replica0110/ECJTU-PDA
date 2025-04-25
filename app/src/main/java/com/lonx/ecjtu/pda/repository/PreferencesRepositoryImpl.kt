package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.data.local.prefs.PreferencesManager
import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class PreferencesRepositoryImpl(
    private val prefs: PreferencesManager
):PreferencesRepository {
    override fun getWeiXinId(): String {
        return prefs.getWeiXinId()
    }
    override fun setWeiXinId(weixinid: String) {
        prefs.setWeiXinId(weixinid)
    }

    override fun hasCredentials(checkIsp: Boolean): Boolean {
        return prefs.hasCredentials(checkIsp)
    }

    override fun getCredentials(): Triple<String, String, Int> {
        return prefs.getCredentials()
    }

    override fun saveCredentials(studentId: String, studentPass: String, ispOption: Int) {
        prefs.saveCredentials(studentId, studentPass, ispOption)
    }

    override fun clearCredentials() {
        prefs.clearCredentials()
    }
}