package com.lonx.ecjtu.pda.network

import com.lonx.ecjtu.pda.data.local.cookies.PersistentCookieJar
import com.lonx.ecjtu.pda.network.SSLManager.getUnsafeSslSocketFactory
import com.lonx.ecjtu.pda.network.SSLManager.getUnsafeTrustManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MyOkHttpClient(
    private val cookieJar: PersistentCookieJar,
    private val timeout: Long
) {
    fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(getUnsafeSslSocketFactory(), getUnsafeTrustManager())
            .hostnameVerifier { _, _ -> true }
            .cookieJar(cookieJar)
            .cache(null)
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

}
