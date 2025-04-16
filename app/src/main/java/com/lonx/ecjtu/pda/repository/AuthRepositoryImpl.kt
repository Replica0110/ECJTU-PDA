package com.lonx.ecjtu.pda.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.local.cookies.PersistentCookieJar
import com.lonx.ecjtu.pda.data.local.prefs.PreferencesManager
import com.lonx.ecjtu.pda.data.remote.ApiConstants
import com.lonx.ecjtu.pda.data.remote.ApiConstants.COOKIE_CASTGC
import com.lonx.ecjtu.pda.data.remote.ApiConstants.COOKIE_JSESSIONID
import com.lonx.ecjtu.pda.data.remote.ApiConstants.ECJTU_LOGIN_URL
import com.lonx.ecjtu.pda.data.remote.ApiConstants.JWXT_ECJTU_DOMAIN
import com.lonx.ecjtu.pda.data.remote.ApiConstants.JWXT_LOGIN_PAGE_IDENTIFIER
import com.lonx.ecjtu.pda.data.remote.ApiConstants.JWXT_LOGIN_URL
import com.lonx.ecjtu.pda.data.remote.ApiConstants.PORTAL_ECJTU_DOMAIN
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException


class AuthRepositoryImpl(
    private val prefs:PreferencesManager,
    private val cookieJar: PersistentCookieJar,
    private val client: OkHttpClient,
    private val gson: Gson
) : AuthRepository {


    override suspend fun hasValidSession(): Boolean = hasLoginInternal(1)

    override suspend fun hasCasTicket(): Boolean = hasLoginInternal(0)

    override suspend fun login(forceRefresh: Boolean): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        val (studentId, studentPassword, _)= prefs.getCredentials()

        val sessionTimeOut = checkSessionValidity()

        if (studentId.isBlank() || studentPassword.isBlank()) {
            Timber.e("登录失败：账号为${studentId}，密码为${studentPassword}。")
            Timber.e("登录失败：PreferencesManager 中缺少凭据。")
            return@withContext ServiceResult.Error("请先设置学号和密码")
        }

        Timber.d("尝试为用户 $studentId 登录。强制刷新：$forceRefresh")

        if (!forceRefresh && hasValidSession() && !sessionTimeOut) {
            Timber.d("用户已拥有 CAS 和 JWXT 会话 Cookie。跳过登录步骤。")
            return@withContext ServiceResult.Success(Unit)
        } else if (!forceRefresh && hasCasTicket()) {
            Timber.d("用户拥有 CAS Cookie (CASTGC)，但可能需要为 JWXT 进行重定向。")
            val redirectResult = handleRedirectionIfNeeded()
            return@withContext if (redirectResult is ServiceResult.Success) {
                if (hasValidSession()) {
                    Timber.d("重定向成功，JWXT 会话已建立")
                    ServiceResult.Success(Unit)
                } else {
                    Timber.w("重定向尝试完成，但 JWXT 会话仍然缺失。")
                    ServiceResult.Error("无法建立教务系统会话")
                }
            } else {
                ServiceResult.Error("登录重定向失败: ${(redirectResult as ServiceResult.Error).message}")
            }
        } else {
            Timber.d("需要执行完整的登录操作 (刷新或未登录)。")
            if (forceRefresh) {
                Timber.i("为刷新清除 Cookie。")
                cookieJar.clear()
            }

            // 调用提取的核心登录序列函数
            val sequenceResult = performCasLoginSequence(studentId, studentPassword)

            // 根据序列结果返回
            return@withContext if (sequenceResult is ServiceResult.Success) {
                Timber.i("用户 $studentId 的自动登录过程成功完成 (通过调用序列)")
                ServiceResult.Success(Unit)
            } else {
                Timber.e("用户 $studentId 的自动登录过程失败 (通过调用序列): ${(sequenceResult as ServiceResult.Error).message}")
                sequenceResult // 返回序列中的错误
            }
        }
    }

    override suspend fun loginManually(studentId: String, studentPass: String, ispOption: Int): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        if (studentId.isBlank() || studentPass.isBlank()) {
            Timber.w("尝试使用空白凭据进行手动登录!")
            return@withContext ServiceResult.Error("账号或密码不能为空")
        }
        Timber.e("尝试为用户 $studentId 手动登录")
        cookieJar.clear()

        val sequenceResult = performCasLoginSequence(studentId, studentPass)

        if (sequenceResult is ServiceResult.Error) {
            Timber.e("用户 $studentId 手动登录失败: ${sequenceResult.message}")
            return@withContext sequenceResult
        }

        Timber.i("用户 $studentId 手动登录成功。正在保存凭据 (ISP: $ispOption)。")
        try {
            prefs.saveCredentials(studentId, studentPass, ispOption)
        } catch (e: Exception) {
            Timber.e(e, "保存凭据时发生错误")
            return@withContext ServiceResult.Error("登录成功但无法保存凭据: ${e.message}")
        }
        return@withContext ServiceResult.Success(Unit)
    }

    override suspend fun logout(clearStoredCredentials: Boolean) = withContext(Dispatchers.IO) {
            Timber.i("正在执行退出登录...")

            cookieJar.clear()
            Timber.d("CookieJar 已清除。")

            if (clearStoredCredentials) {
                prefs.clearCredentials()
                Timber.d("存储的凭据已清除。")
            } else {
                Timber.d("选择不清除存储的凭据。")
            }

            Timber.i("退出登录操作完成。")
        }


    override suspend fun checkSessionValidity(): Boolean = withContext(Dispatchers.IO) {
        if (!hasValidSession()) {
            Timber.d("教务系统会话检查: 由于缺少会话 Cookie，无法进行会话检查。")
            return@withContext false
        }

        Timber.d("教务系统会话检查: 正在检查会话 Cookie 的有效性。")

        val checkUrl = ApiConstants.GET_STU_INFO_URL
        val headers = Headers.Builder()
            .add("Host", checkUrl.toHttpUrl().host)
            .add("User-Agent", ApiConstants.USER_AGENT)
            .add("Referer", "$JWXT_ECJTU_DOMAIN/index.action")
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val request = Request.Builder()
            .url(checkUrl)
            .headers(headers)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()

            response.use {
                if (!it.isSuccessful) {
                    Timber.w("JWXT Session Check: Failed (HTTP ${it.code}).")

                    return@withContext false
                }

                val htmlBody = it.body?.string()

                if (htmlBody.isNullOrBlank()) {
                    Timber.w("JWXT Session Check: Failed (Response body is empty).")
                    return@withContext false
                }

                if (htmlBody.contains(JWXT_LOGIN_PAGE_IDENTIFIER)) {
                    Timber.w("JWXT Session Check: Failed (Detected login page content). Session likely expired.")
                    return@withContext false
                } else {
                    Timber.i("JWXT Session Check: Success. Session appears valid.")
                    return@withContext true
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "JWXT Session Check: Failed (Network IO Error).")
            return@withContext false
        } catch (e: Exception) {
            Timber.e(e, "JWXT Session Check: Failed (Unknown Error).")
            return@withContext false
        }
    }

    override suspend fun updatePassword(oldPassword: String, newPassword: String): ServiceResult<String> = withContext(Dispatchers.IO) {
        Timber.e("开始修改密码...")
        if (newPassword.isBlank()) {
            Timber.w("修改密码失败：新密码不能为空。")
            return@withContext ServiceResult.Error("新密码不能为空")
        }

        if (!checkSessionValidity()) {
            Timber.w("修改密码失败：会话无效。")
            return@withContext ServiceResult.Error("请先登录或刷新会话")
        }

        try {
            val updatePasswordUrl = ApiConstants.UPDATE_PASSWORD
            val formBody = FormBody.Builder()
                .add("oldPassword", oldPassword)
                .add("password", newPassword)
                .add("rpassword", newPassword)
                .build()

            val headers = Headers.Builder()
                .add("User-Agent", ApiConstants.USER_AGENT)
                .add("Host", JWXT_ECJTU_DOMAIN)
                .add("Origin", "https://$JWXT_ECJTU_DOMAIN")
                .add("Referer", ApiConstants.GET_STU_INFO_URL)
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            val request = Request.Builder()
                .url(updatePasswordUrl)
                .headers(headers)
                .post(formBody)
                .build()

            Timber.e("正在发送 POST 请求修改密码...")
            val response = client.newCall(request).execute()

            response.use {
                if (!it.isSuccessful) {
                    val errorBody = it.body?.string()
                    Timber.e("修改密码请求失败: HTTP ${it.code}. Body: $errorBody")
                    return@withContext ServiceResult.Error("修改密码请求失败: HTTP ${it.code}")
                }

                val responseBody = it.body?.string()?.trim()
                Timber.i("修改密码响应: '$responseBody'")

                when (responseBody) {
                    "1" -> ServiceResult.Success("密码修改成功")
                    "2" -> ServiceResult.Error("旧密码错误或新密码不符合要求")
                    else -> ServiceResult.Error("修改密码失败：未知的响应 '$responseBody'")
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "修改密码时网络错误")
            ServiceResult.Error("网络错误: ${e.message}")
        } catch (e: Exception) {
            Timber.e(e, "修改密码时未知错误")
            ServiceResult.Error("发生未知错误: ${e.message}")
        }
    }



    private fun hasLoginInternal(type: Int): Boolean {
        // 需要检查的 URL 列表 (CAS 和 JWXT 的登录相关 URL)
        val urlsToCheck = listOf(ECJTU_LOGIN_URL.toHttpUrl(), JWXT_LOGIN_URL.toHttpUrl(),
            PORTAL_ECJTU_DOMAIN.toHttpUrl())
        // 从 CookieJar 加载所有相关 URL 的 Cookie
        val allCookies = urlsToCheck.flatMap { url ->
            try {
                cookieJar.loadForRequest(url)
            } catch (e: Exception) {
                Timber.w(e, "加载 $url 的 Cookie 失败")
                emptyList() // 出错则返回空列表
            }
        }

        return when (type) {
            0 -> allCookies.any { it.name == COOKIE_CASTGC && it.value.isNotBlank() }
            1 -> listOf(COOKIE_CASTGC, COOKIE_JSESSIONID).all { cookieName ->
                allCookies.any { it.name == cookieName && it.value.isNotBlank() }
            }
            else -> false // 其他类型无效
        }
    }

    private suspend fun performCasLoginSequence(studentId: String, plainPassword: String): ServiceResult<Unit> {
        Timber.d("执行用户 $studentId 的 CAS 登录序列...")

        // 1. 加密密码
        val encPassword = when (val encPasswordResult = getEncryptedPassword(plainPassword)) {
            is ServiceResult.Success -> encPasswordResult.data
            is ServiceResult.Error -> {
                Timber.e("CAS 登录序列在密码加密时失败: ${encPasswordResult.message}")
                // 直接返回错误，不继续执行
                return ServiceResult.Error("密码加密失败: ${encPasswordResult.message}")
            }
        }

        // 2. 构建通用 CAS 请求头
        val headers = Headers.Builder()
            .add("User-Agent", ApiConstants.USER_AGENT)
            .add("Host", ApiConstants.CAS_ECJTU_DOMAIN)
            .build()

        // 3. 获取 LT 值
        val ltValue = when (val ltValueResult = getLoginLtValue(headers)) {
            is ServiceResult.Success -> ltValueResult.data
            is ServiceResult.Error -> {
                Timber.e("CAS 登录序列在获取 LT 值时失败: ${ltValueResult.message}")
                return ServiceResult.Error("无法获取登录令牌: ${ltValueResult.message}")
            }
        }

        // 4. 执行登录 POST 请求
        when (val loginResponseResult = loginWithCredentials(studentId, encPassword, ltValue, headers)) {
            is ServiceResult.Success -> {
                if (!hasCasTicket()) {
                    Timber.e("CAS 登录序列失败：提交凭据后未找到 CASTGC Cookie。")
                    // 假设是凭据错误
                    return ServiceResult.Error("账号或密码错误")
                }
                Timber.d("CAS 登录序列：初始登录 POST 成功 (找到 CASTGC)。")
            }
            is ServiceResult.Error -> {
                Timber.e("CAS 登录序列在凭据提交时失败: ${loginResponseResult.message}")
                // 传递来自 loginWithCredentials 的具体错误消息
                return ServiceResult.Error(loginResponseResult.message)
            }
        }

        // 5. 处理重定向
        when (val redirectResult = handleRedirection(headers)) {
            is ServiceResult.Success -> {
                Timber.d("CAS 登录序列：重定向处理成功。")
            }
            is ServiceResult.Error -> {
                Timber.e("CAS 登录序列在重定向时失败: ${redirectResult.message}")
                return ServiceResult.Error("登录重定向失败: ${redirectResult.message}")
            }
        }

        if (!hasValidSession()) {
            Timber.w("CAS 登录序列完成，但 JWXT 会话 Cookie (JSESSIONID) 可能缺失。")
            return ServiceResult.Error("无法建立教务系统会话")
        }

        Timber.i("用户 $studentId 的 CAS 登录序列成功完成。")
        return ServiceResult.Success(Unit)
    }

    private suspend fun getEncryptedPassword(plainPassword: String): ServiceResult<String> = safeAuthCall {
        // 构建表单体，包含需要加密的密码
        val formBody = FormBody.Builder()
            .add("pwd", plainPassword)
            .build()
        // 构建请求
        val request = Request.Builder()
            .url(ApiConstants.PWD_ENC_URL) // 加密服务的 URL
            .post(formBody)
            .addHeader("User-Agent", ApiConstants.USER_AGENT)
            .build()
        Timber.d("正在加密密码...")
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("密码加密失败: HTTP ${response.code}")
        }
        val responseBody = response.body?.string() ?: "" // 获取响应体字符串
        val passwordEnc = try {
            // 解析 JSON 并获取 "passwordEnc" 字段
            gson.fromJson(responseBody, JsonObject::class.java)
                ?.get("passwordEnc")?.asString
        } catch (e: Exception) { null } // 解析失败则返回 null

        // 检查是否成功获取到加密后的密码
        if (passwordEnc.isNullOrBlank()){
            Timber.e("在加密响应中找不到 'passwordEnc': $responseBody")
            throw IOException("在加密响应中找不到 'passwordEnc'")
        }

        Timber.d("密码加密成功。")
        passwordEnc // 返回加密后的密码字符串
    }

    private suspend fun getLoginLtValue(headers: Headers): ServiceResult<String> = safeAuthCall {
        Timber.d("正在从登录页面获取 LT 值: $ECJTU_LOGIN_URL")
        // 构建 GET 请求以获取登录页面 HTML
        val request = Request.Builder()
            .url(ECJTU_LOGIN_URL)
            .headers(headers)
            .get()
            .build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("获取登录页面失败: HTTP ${response.code}")
        }
        val body = response.body?.string() // 获取 HTML 响应体
        val document = body?.let { Jsoup.parse(it) } // 使用 Jsoup 解析 HTML
        // 从 HTML 中提取 name="lt" 的 input 元素的 value 属性
        val ltValue = document?.select("input[name=lt]")?.attr("value")

        // 检查是否成功获取到 LT 值
        if (ltValue.isNullOrBlank()) {
            Timber.e("在登录页面 HTML 中找不到 'lt' 值。正文: ${body?.take(500)}") // 记录正文开头部分以供调试
            throw IOException("在登录页面 HTML 中找不到 'lt' 值")
        }

        Timber.d("LT 值已获取: $ltValue")
        ltValue // 返回获取到的 LT 值
    }

    /** 执行实际的 CAS 登录 POST 请求。 */
    private suspend fun loginWithCredentials(
        username: String,
        encryptedPass: String,
        ltValue: String,
        headers: Headers
    ): ServiceResult<Unit> = safeAuthCall {
        Timber.d("正在为 $username 提交登录凭据...")
        // 构建登录表单体
        val loginRequestBody = FormBody.Builder()
            .add("username", username)
            .add("password", encryptedPass) // 使用加密后的密码
            .add("lt", ltValue)             // 使用获取到的 LT 值
            .build()

        // 构建 POST 请求
        val request = Request.Builder()
            .url(ECJTU_LOGIN_URL) // 提交到 CAS 登录 URL
            .post(loginRequestBody)
            .headers(headers) // 包含 Host, User-Agent
            .addHeader("Content-Type", "application/x-www-form-urlencoded") // 表单提交类型
            .addHeader("Referer", ECJTU_LOGIN_URL) // 设置 Referer 为登录页面本身
            .build()

        // 执行调用，不自动跟踪重定向，以便检查初始响应
        // 我们需要在隐式重定向前检查即时响应中的 Cookie (特别是 CASTGC)
        val tempClient = client.newBuilder().followRedirects(false).build() // 创建一个不自动重定向的临时客户端
        val loginResponse = tempClient.newCall(request).execute()

        loginResponse.use { response ->
            Timber.d("登录 POST 响应代码: ${response.code}")
            response.headers("Set-Cookie").forEach { cookie -> Timber.v("Set-Cookie: $cookie") }

            val hasCastgcInHeader = response.headers("Set-Cookie").any { c -> c.contains(COOKIE_CASTGC) }
            val cookiesFromJar = cookieJar.loadForRequest(ECJTU_LOGIN_URL.toHttpUrl())
            val hasCastgcInJar = cookiesFromJar.any { c -> c.name == COOKIE_CASTGC && c.value.isNotBlank() }

            if (!hasCastgcInHeader && !hasCastgcInJar) {
                // 没有 CASTGC 意味着登录失败，很可能是凭据错误
                var errorMsg: String? = null
                try {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        Timber.v("尝试从响应体解析错误信息...")
                        errorMsg = Jsoup.parse(bodyString)
                            .select("div.mistake_notice")
                            .first()
                            ?.text()
                            ?.trim()

                        if (!errorMsg.isNullOrBlank()) {
                            Timber.d("从 'div.mistake_notice' 解析到错误信息: '$errorMsg'")
                        } else {
                            Timber.d("'div.mistake_notice' 未找到或为空。检查是否有其他错误格式。")
                        }

                    } else {
                        Timber.d("CASTGC missing and response body was null or empty.")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "解析登录错误响应体时出错。")
                }
            }
            if (!response.isRedirect && response.code != 200) {
                Timber.w("登录 POST 返回 ${response.code} 但找到了 CASTGC")
            } else if (response.isRedirect) {
                Timber.d("登录 POST 成功，收到重定向响应 (HTTP ${response.code})，且找到 CASTGC")
            } else { // code is 200
                Timber.d("登录 POST 成功，收到 200 OK 响应，且找到 CASTGC。")
            }

        }
    }


    private suspend fun handleRedirection(headers: Headers): ServiceResult<Unit> = safeAuthCall {
        Timber.e("正在处理登录后重定向...")
        val jwxtLoginUrlHttp = JWXT_LOGIN_URL.toHttpUrlOrNull() ?: throw IOException("Invalid JWXT Login URL")
        Timber.e("正在访问 JWXT 登录 URL: $jwxtLoginUrlHttp")
        val jwxtLoginRequest = Request.Builder()
            .url(jwxtLoginUrlHttp)
            .headers(headers.newBuilder()
                .removeAll("Host")
                .removeAll("Referer")
                .removeAll("Content-Type")
                .add("Host", jwxtLoginUrlHttp.host)
                .build())
            .get()
            .build()
        client.newCall(jwxtLoginRequest).execute().use { response ->
            Timber.e("JWXT 登录 URL 访问最终响应代码: ${response.code}")
            if (!response.isSuccessful) Timber.w("JWXT 登录重定向步骤的最终响应不成功 (HTTP ${response.code})")
        }
        delay(50)
        val casToJwxtUrlHttp = ApiConstants.ECJTU2JWXT_URL.toHttpUrlOrNull() ?: throw IOException("Invalid CAS->JWXT URL")
        Timber.e("正在访问显式 CAS 服务 URL: $casToJwxtUrlHttp")
        val finalRedirectRequest = Request.Builder()
            .url(casToJwxtUrlHttp)
            .headers(headers)
            .get()
            .build()
        client.newCall(finalRedirectRequest).execute().use { response ->
            Timber.e("显式 CAS->JWXT 重定向最终响应代码: ${response.code}")
            if (!response.isSuccessful) Timber.w("最终 CAS->JWXT 重定向步骤的最终响应不成功 (HTTP ${response.code})")
        }
        Timber.e("重定向处理完成。")
    }

    private suspend fun handleRedirectionIfNeeded(): ServiceResult<Unit> {
        if (hasCasTicket() && !hasValidSession()) {
            Timber.e("找到 CASTGC，但缺少 JWXT 会话。正在执行重定向。")
            val headers = Headers.Builder()
                .add("User-Agent", ApiConstants.USER_AGENT)
                .add("Host", ApiConstants.CAS_ECJTU_DOMAIN)
                .build()
            val result = handleRedirection(headers)
            return if (result is ServiceResult.Success && hasValidSession()) {
                ServiceResult.Success(Unit)
            } else {
                ServiceResult.Error("重定向失败或未建立完整会话")
            }
        } else {
            Timber.e("不需要重定向 (缺少 CASTGC 或已有 JWXT 会话)。")
            return if (hasValidSession()) ServiceResult.Success(Unit) else ServiceResult.Error("无法执行重定向 (缺少 CAS 凭据)")
        }
    }

    private suspend fun <T> safeAuthCall(authCall: suspend () -> T): ServiceResult<T> = withContext(Dispatchers.IO) {
        try {
            ServiceResult.Success(authCall())
        } catch (e: AuthException) {
            Timber.w("认证失败: ${e.message}")
            ServiceResult.Error(e.message ?: "认证失败", e)
        } catch (e: IOException) {
            Timber.e(e, "认证过程中网络或IO错误")
            ServiceResult.Error("网络错误: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "认证过程中未知错误")
            ServiceResult.Error("发生未知错误: ${e.message}", e)
        }
    }

    class AuthException(message: String) : IOException(message)
}