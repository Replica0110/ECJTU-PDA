package com.lonx.ecjtu.pda.network

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

object SSLManager {
    fun getUnsafeTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {

            }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {

            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }

    fun getUnsafeSslSocketFactory(): SSLSocketFactory {
        val trustManager = getUnsafeTrustManager()
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return sslContext.socketFactory
    }
}