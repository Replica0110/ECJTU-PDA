package com.lonx.ecjtu.pda.data.local.cookies;

import android.content.Context
import android.content.SharedPreferences
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor
import com.franmontiel.persistentcookiejar.persistence.SerializableCookie
import okhttp3.Cookie

class SharedPrefsCookiePersistor(private val sharedPreferences: SharedPreferences) : CookiePersistor {

    constructor(context: Context) : this(context.getSharedPreferences("CookiePersistence", Context.MODE_PRIVATE))

    override fun loadAll(): List<Cookie> {
        val cookies = mutableListOf<Cookie>()
        for ((_, value) in sharedPreferences.all) {
            val serializedCookie = value as String
            val cookie = SerializableCookie().decode(serializedCookie)
            cookie?.let {
                cookies.add(it)
            }
        }
        return cookies
    }

    override fun saveAll(cookies: Collection<Cookie>) {
        val editor = sharedPreferences.edit()
        cookies.forEach { cookie ->
            editor.putString(createCookieKey(cookie), SerializableCookie().encode(cookie))
        }
        editor.apply()
    }

    override fun removeAll(cookies: Collection<Cookie>) {
        val editor = sharedPreferences.edit()
        cookies.forEach { cookie ->
            editor.remove(createCookieKey(cookie))
        }
        editor.apply()
    }

    private fun createCookieKey(cookie: Cookie): String {
        return "${if (cookie.secure) "https" else "http"}://${cookie.domain}${cookie.path}|${cookie.name}"
    }

    override fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
