package com.lonx.ecjtu.pda.utils

import android.content.Context
import android.content.SharedPreferences

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
    fun hasCredentials(): Boolean {
        return preferences.contains("student_id") && preferences.contains("password")
    }

    private fun edit(function: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply {
            function()
            apply()
        }
    }
    /**保存账号及密码 */
    fun saveCredentials(studentId: String, password: String) {
        edit {
            putString("student_id", studentId)
            putString("password", password)
        }
    }
    /*清空账号及密码*/
    fun clearCredentials() {
        edit {
            remove("student_id")
            remove("password")
        }
    }
}