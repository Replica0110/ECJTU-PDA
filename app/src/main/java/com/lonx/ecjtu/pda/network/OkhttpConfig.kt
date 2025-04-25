package com.lonx.ecjtu.pda.network

import okhttp3.CookieJar

data class OkHttpConfig(
    val timeoutSeconds: Long = 5,
    val cookieJar: CookieJar? = null,
    val useUnsafeSSL: Boolean = true,
    val followRedirects: Boolean = true
)
