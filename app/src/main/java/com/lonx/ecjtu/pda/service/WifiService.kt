package com.lonx.ecjtu.pda.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.NetworkType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import slimber.log.d
import slimber.log.e

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class WifiService: BaseService {
    private val loginOutUrl = "http://172.16.2.100:801/eportal/?c=ACSetting&a=Logout&wlanuserip=null&wlanacip=null&wlanacname=null&port=&hostname=172.16.2.100&iTermType=1&session=null&queryACIP=0&mac=00-00-00-00-00-00"
    private val loginInUrl = "http://172.16.2.100:801/eportal/?c=ACSetting&a=Login&protocol=http:&hostname=172.16.2.100&iTermType=1&wlanacip=null&wlanacname=null&mac=00-00-00-00-00-00&enAdvert=0&queryACIP=0&loginMethod=1"
    fun getNetworkType(context: Context): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

        val networkType = when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkType.WIFI
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkType.CELLULAR
            else -> NetworkType.UNKNOWN
        }
        return networkType
    }
    fun login(studentID: String, passwordECJTU: String, theISP: Int): String {
        if (studentID.isEmpty()) {
            return "E3 您没有填写学号！"
        }
        if (passwordECJTU.isEmpty()) {
            return "E3 您没有填写密码！"
        }
        val strTheISP = when (theISP) {
            1 -> "cmcc"
            2 -> "telecom"
            else -> "unicom"
        }

        d {"开始创建OkHttpClient对象"}
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        d { "开始准备请求头" }
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val postBody = "DDDDD=%2C0%2C$studentID@$strTheISP&upass=$passwordECJTU&R1=0&R2=0&R3=0&R6=0&para=00&0MKKey=123456&buttonClicked=&redirect_url=&err_flag=&username=&password=&user=&cmd=&Login="
        val request = Request.Builder()
            .url(loginInUrl)
            .post(postBody.toRequestBody(mediaType))
            .build()

        d { "开始发送请求" }
        val call = client.newCall(request)
        return try {
            val response = call.execute()
            val headers = response.headers
            val location = headers["Location"]
            if (location != null) {
                if (!location.contains("RetCode=")) {
                    return "登录成功！"
                }
                val startIndex = location.indexOf("RetCode=") + 8
                val endIndex = location.indexOf("&", startIndex)
                if (startIndex >= 0 && endIndex >= 0) {
                    return when (location.substring(startIndex, endIndex)) {
                        "userid error1" -> "E3 账号不存在(可能未绑定宽带账号或运营商选择有误)"
                        "userid error2" -> "E3 密码错误"
                        "512" -> "E3 AC认证失败(可能重复登录)"
                        "Rad:Oppp error: Limit Users Err" -> "E3 超出校园网设备数量限制"
                        else -> {
                            e { "未知错误码" }
                            "E4 登录失败：\n未知错误"
                        }
                    }
                }
                e{"错误码为空：${headers}"}
                "E2 无法解析回包数据：$headers"
            } else {
                e { "登录返回头找不到重定向字段：${headers}" }
                "E1 无法解析回包数据：$headers"
            }
        } catch (e: IOException) {
            e(e) { "登录请求异常"}
            "E0 发送登录请求失败，捕获到异常：${e.message}"
        }
    }
    fun loginOut(): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val request = Request.Builder()
            .url(loginOutUrl)
            .post(
                body = "".toRequestBody(mediaType)
            )
            .build()

        val response = client.newCall(request).execute()
        val location = response.headers["Location"]
        if (location != null) {
            return if (location.contains("ACLogOut=1")) {
                "注销成功！"
            } else if (location.contains("ACLogOut=2")) {
                "注销失败，未连接网络或连接的不是校园网"
            } else {
                "注销失败，未知错误！"
            }
        }
        return "注销失败，未知错误！"
    }
    fun getState(): Int {
        d { "开始创建OkHttpClient对象" }
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()

        d{"开始准备请求头"}
        val request = Request.Builder()
            .url("http://172.16.2.100")
            .get()
            .build()

        d{"开始发送请求"}
        val call = client.newCall(request)
        return try {
            val response = call.execute()
            if (response.code == 200) {
                d{"get请求成功"}
                val responseBody = response.body?.string() ?: ""
                if (responseBody.contains("<title>注销页</title>")) {
                    4
                } else {
                    3
                }
            } else {
                e{"奇怪的http状态码：${response.code}"}
                2
            }
        } catch (e: IOException) {
            when (e) {
                is SocketTimeoutException -> 2
                is ConnectException -> 1
                else -> {
                    e{"奇怪的异常捕获：$e"}
                    2
                }
            }
        }
    }
}