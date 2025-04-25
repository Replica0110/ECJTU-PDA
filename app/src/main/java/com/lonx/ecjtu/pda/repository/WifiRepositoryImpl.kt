package com.lonx.ecjtu.pda.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lonx.ecjtu.pda.common.NetworkType
import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.local.prefs.PreferencesManager
import com.lonx.ecjtu.pda.data.model.CampusNetStatus
import com.lonx.ecjtu.pda.domain.repository.WifiRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class WifiRepositoryImpl(
    private val prefs: PreferencesManager,
    private val client: OkHttpClient
) : WifiRepository {

    private val loginOutUrl =
        "http://172.16.2.100:801/eportal/?c=ACSetting&a=Logout&wlanuserip=null&wlanacip=null&wlanacname=null&port=&hostname=172.16.2.100&iTermType=1&session=null&queryACIP=0&mac=00-00-00-00-00-00"

    private val loginInUrl =
        "http://172.16.2.100:801/eportal/?c=ACSetting&a=Login&protocol=http:&hostname=172.16.2.100&iTermType=1&wlanacip=null&wlanacname=null&mac=00-00-00-00-00-00&enAdvert=0&queryACIP=0&loginMethod=1"

    override suspend fun getNetworkType(context: Context): NetworkType {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

        return when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkType.WIFI
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkType.CELLULAR
            else -> NetworkType.UNKNOWN
        }
    }

    override suspend fun login(): PDAResult<String> {
        val (stuId, stuPass, stuISP) = prefs.getCredentials()
        if (stuId.isBlank() || stuPass.isBlank() || stuISP == 0) {
            return PDAResult.Error(message = "账号或密码或运营商为空！")
        }

        val strTheISP = when (stuISP) {
            1 -> "cmcc"
            2 -> "telecom"
            else -> "unicom"
        }

        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val postBody =
            "DDDDD=%2C0%2C$stuId@$strTheISP&upass=$stuPass&R1=0&R2=0&R3=0&R6=0&para=00&0MKKey=123456&buttonClicked=&redirect_url=&err_flag=&username=&password=&user=&cmd=&Login="

        val request = Request.Builder()
            .url(loginInUrl)
            .post(postBody.toRequestBody(mediaType))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val location = response.header("Location")

            if (location != null) {
                if (!location.contains("RetCode=")) {
                    return PDAResult.Success("登录成功！")
                }

                val startIndex = location.indexOf("RetCode=") + 8
                val endIndex = location.indexOf("&", startIndex)
                if (startIndex >= 0 && endIndex >= 0) {
                    val errorCode = location.substring(startIndex, endIndex)
                    val message = mapOf(
                        "userid error1" to "账号不存在(可能未绑定宽带账号或运营商选择有误)",
                        "userid error2" to "密码错误",
                        "512" to "AC认证失败(可能重复登录)",
                        "Rad:Oppp error: Limit Users Err" to "超出校园网设备数量限制"
                    )[errorCode] ?: "未知错误"
                    return PDAResult.Error(message)
                }

                PDAResult.Error("无法解析回包数据：$location")
            } else {
                PDAResult.Error("登录失败，未返回重定向地址")
            }
        } catch (e: IOException) {
            PDAResult.Error("发送登录请求失败，捕获到异常：${e.message}")
        }
    }

    override suspend fun logout(clearStoredCredentials: Boolean): PDAResult<String> {
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val request = Request.Builder()
            .url(loginOutUrl)
            .post("".toRequestBody(mediaType))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val location = response.header("Location")

            when {
                location?.contains("ACLogOut=1") == true -> PDAResult.Success("注销成功！")
                location?.contains("ACLogOut=2") == true -> PDAResult.Error("注销失败，未连接网络或连接的不是校园网")
                else -> PDAResult.Error("注销失败，未知错误！")
            }
        } catch (e: IOException) {
            PDAResult.Error("注销请求异常：${e.message}")
        }
    }

    override suspend fun getStatus(): CampusNetStatus {
        val request = Request.Builder()
            .url("http://172.16.2.100")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.code == 200) {
                val responseBody = response.body?.string().orEmpty()
                if (responseBody.contains("<title>注销页</title>")) {
                    CampusNetStatus.LOGGED_IN
                } else {
                    CampusNetStatus.NOT_LOGGED_IN
                }
            } else {
                CampusNetStatus.UNKNOWN_ERROR
            }
        } catch (e: IOException) {
            when (e) {
                is SocketTimeoutException -> CampusNetStatus.SOCKET_ERROR
                is ConnectException -> CampusNetStatus.CONNECTION_ERROR
                else -> CampusNetStatus.UNKNOWN_ERROR
            }
        }
    }
}
