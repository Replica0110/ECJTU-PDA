package com.lonx.ecjtu.pda.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.ApiConstants
import com.lonx.ecjtu.pda.data.ApiConstants.COOKIE_CASTGC
import com.lonx.ecjtu.pda.data.ApiConstants.COOKIE_JSESSIONID
import com.lonx.ecjtu.pda.data.ApiConstants.ECJTU_LOGIN_URL
import com.lonx.ecjtu.pda.data.ApiConstants.GET_SCORE_URL
import com.lonx.ecjtu.pda.data.ApiConstants.JWXT_ECJTU_DOMAIN
import com.lonx.ecjtu.pda.data.ApiConstants.JWXT_LOGIN_PAGE_IDENTIFIER
import com.lonx.ecjtu.pda.data.ApiConstants.JWXT_LOGIN_URL
import com.lonx.ecjtu.pda.data.ApiConstants.PORTAL_ECJTU_DOMAIN
import com.lonx.ecjtu.pda.data.PrefKeys.PASSWORD
import com.lonx.ecjtu.pda.data.PrefKeys.STUDENT_ID
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.utils.PersistentCookieJar
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.jsoup.Jsoup
import timber.log.Timber

class JwxtService(
    private val prefs: PreferencesManager, // 用于存储和读取设置（如学号密码）
    private val cookieJar: PersistentCookieJar,         // 用于持久化存储 Cookie
    private val client: OkHttpClient                     // 用于执行网络请求
): BaseService {
    private val maxRetries = 3 // 最大重试次数
    private val maxLoginAttempts = 2
    private val gson = Gson()  // 用于 JSON 解析
    private val reLoginMutex = Mutex()

    init {
        Timber.d("JwxtService 初始化完成。")
    }

    /**
     * 根据 Cookie 是否存在检查用户是否已登录。
     * 类型 0: 检查 CASTGC (主要的 CAS 票据)。
     * 类型 1: 同时检查 CASTGC 和 JSESSIONID (特别是针对教务系统 JWXT)。
     */
    private fun hasLogin(type: Int = 0): Boolean {
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
    /**
     * 执行完整的 CAS 登录和重定向序列。
     * @param studentId 学号
     * @param plainPassword 明文密码
     * @return ServiceResult<Unit> 成功或失败
     */
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
                // 检查 CASTGC 是否确实已设置 (loginWithCredentials 内部可能已经检查过，这里是双重保险)
                if (!hasLogin(0)) { // Type 0 for CASTGC
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

        // 6. 最终检查 (JWXT 会话 - JSESSIONID)
        if (!hasLogin(1)) { // Type 1 for JSESSIONID
            Timber.w("CAS 登录序列完成，但 JWXT 会话 Cookie (JSESSIONID) 可能缺失。")
            return ServiceResult.Error("无法建立教务系统会话")
        }

        Timber.i("用户 $studentId 的 CAS 登录序列成功完成。")
        return ServiceResult.Success(Unit)
    }
    /**
     * 尝试使用存储在 PreferencesManager 中的凭据进行登录。
     * 处理密码加密、获取 LT 值和重定向。
     * @param [forceRefresh] 是否强制刷新登录，即使已经登录。
     * @return [ServiceResult] 登录结果。
     */
    suspend fun login(forceRefresh: Boolean = false): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        val studentId = prefs.getString(STUDENT_ID, "")
        val studentPassword = prefs.getString(PASSWORD, "")
        val sessionTimeOut = checkSession()

        if (studentId.isBlank() || studentPassword.isBlank()) {
            Timber.e("登录失败：账号为${studentId}，密码为${studentPassword}。")
            Timber.e("登录失败：PreferencesManager 中缺少凭据。")
            return@withContext ServiceResult.Error("请先设置学号和密码")
        }

        Timber.d("尝试为用户 $studentId 登录。强制刷新：$forceRefresh")

        if (!forceRefresh && hasLogin(1) && !sessionTimeOut) {
            Timber.d("用户已拥有 CAS 和 JWXT 会话 Cookie。跳过登录步骤。")
            return@withContext ServiceResult.Success(Unit)
        } else if (!forceRefresh && hasLogin(0)) {
            Timber.d("用户拥有 CAS Cookie (CASTGC)，但可能需要为 JWXT 进行重定向。")
            val redirectResult = handleRedirectionIfNeeded()
            return@withContext if (redirectResult is ServiceResult.Success) {
                if (hasLogin(1)) {
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


    /**
     * 执行退出登录操作。
     * 清除网络会话 (Cookies) 和可选地清除存储的凭据。
     * @param [clearStoredCredentials] 是否同时清除 PreferencesManager 中保存的学号密码，默认为 true。
     */
    fun logout(clearStoredCredentials: Boolean = true) {
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
    /**
     * 登录过期检查方法
     * @return true 表示会话有效，false 表示会话已过期。
    */
    suspend fun checkSession(): Boolean = withContext(Dispatchers.IO) {
        if (!hasLogin(1)) {
            Timber.d("JWXT Session Check: Failed (JSESSIONID cookie missing).")
            return@withContext false
        }

        Timber.d("JWXT Session Check: Performing network check...")

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


    /**
     * 修改密码
     * @param [oldPassword] 旧密码
     * @param [newPassword] 新密码
     * @return [ServiceResult] 修改密码结果
     */
    suspend fun updatePassword(oldPassword: String, newPassword: String): ServiceResult<String> = withContext(Dispatchers.IO) {
        Timber.d("开始修改密码...")

        if (newPassword.isBlank()) {
            Timber.w("修改密码失败：新密码不能为空。")
            return@withContext ServiceResult.Error("新密码不能为空")
        }

        if (!checkSession()) {
            Timber.d("JWXT 会话无效或无法验证，尝试登录...")
            val loginResult = login()
            if (loginResult is ServiceResult.Error) {
                Timber.e("修改密码失败：需要登录，但登录失败: ${loginResult}")
                return@withContext ServiceResult.Error("请先登录: ${loginResult}")
            }
            delay(100)
            if (!checkSession()) {
                Timber.e("修改密码失败：登录尝试后 JWXT 会话仍然无效。")
                return@withContext ServiceResult.Error("无法建立教务系统会话，请重新登录")
            }
            Timber.d("登录/会话确认成功，继续修改密码。")
        } else {
            Timber.d("JWXT 会话有效，直接修改密码。")
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
                .add("Accept", "*/*")
                .add("Accept-Encoding", "gzip, deflate, br, zstd")
                .add("Host", JWXT_ECJTU_DOMAIN)
                .add("Origin", "https://${JWXT_ECJTU_DOMAIN}")
                .add("Referer", ApiConstants.GET_STU_INFO_URL)
                .add("X-Requested-With", "XMLHttpRequest")
                .add("sec-ch-ua", "\"Google Chrome\";v=\"135\", \"Not-A.Brand\";v=\"8\", \"Chromium\";v=\"135\"")
                .add("Sec-Fetch-Site", "same-origin")
                .add("sec-ch-ua-mobile", "?0")
                .add("Sec-Fetch-Mode", "cors")
                .add("Sec-Fetch-Dest", "empty")
                .build()

            // 构建 POST 请求
            val request = Request.Builder()
                .url(updatePasswordUrl)
                .headers(headers)
                .post(formBody) // 使用 FormBody
                .build()

            Timber.d("正在向 $updatePasswordUrl 发送 POST 请求修改密码 (Form Data)")
            val response = client.newCall(request).execute()

            response.use {
                if (!it.isSuccessful) {
                    Timber.e("修改密码请求失败: HTTP ${it.code} ${it.message}")
                    // 打印响应体帮助调试，即使请求不成功
                    val errorBody = it.body?.string()
                    Timber.e("失败响应体: $errorBody")
                    return@withContext ServiceResult.Error("修改密码请求失败: HTTP ${it.code}")
                }

                val responseBody = it.body?.string()?.trim()
                // *** 注意：这里打印修改为了 Timber.d 或 Timber.i，因为原始文本不是错误 ***
                Timber.i("修改密码响应原始文本: '$responseBody'")

                if (responseBody.isNullOrBlank()) {
                    Timber.e("修改密码响应体为空。")
                    return@withContext ServiceResult.Error("修改密码响应体为空")
                }

                when (responseBody) {
                    "1" -> {
                        Timber.i("密码修改成功。")
                        ServiceResult.Success("密码修改成功")
                    }
                    "2" -> {
                        Timber.w("修改密码失败：服务器返回 '2' (可能是旧密码错误、新密码不符合要求或请求格式错误)。")
                        ServiceResult.Error("旧密码错误或新密码不符合要求")
                    }
                    else -> {
                        Timber.e("修改密码失败：收到未知的响应内容 '$responseBody'")
                        ServiceResult.Error("修改密码失败：未知的响应内容 '$responseBody'")
                    }
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "网络或IO错误在修改密码时发生")
            ServiceResult.Error("网络错误，请稍后重试: ${e.message}")
        } catch (e: Exception) {
            Timber.e(e, "未知错误在修改密码时发生")
            ServiceResult.Error("发生未知错误: ${e.message}")
        }
    }

    class PasswordChangeException(message: String) : IOException(message)
    /**
     * 获取 YKT (一卡通) 余额。
     * @return [ServiceResult] 包含一卡通余额的字符串，或错误信息
    */
    suspend fun getYktNum(): ServiceResult<String> = withContext(Dispatchers.IO) {
        // 1. 确保 CAS 登录 (访问 DCP 通常需要 CAS 认证)
        if (!hasLogin(0)) { // 检查 CASTGC
            val loginResult = login() // 尝试登录
            if (loginResult is ServiceResult.Error) {
                return@withContext ServiceResult.Error("需要登录: ${loginResult}")
            }
            // 短暂延迟可能有助于确保 Cookie 传播
            delay(100)
        }

        val dcpCookieResult = safeServiceCall {
            Timber.d("正在访问 DCP URL 以确保 Cookie: ${ApiConstants.DCP_URL}")
            val request = Request.Builder().url(ApiConstants.DCP_URL).get().build()
            client.newCall(request).execute().close() // 发出请求并立即关闭响应体
        }
        if (dcpCookieResult is ServiceResult.Error) {
            // 记录警告，但可能仍然尝试继续，因为有时这不是必需的
            Timber.w("在调用 getYktNum 之前访问 DCP 基础 URL 失败: ${dcpCookieResult.message}")
        }

        // 3. 发出 DCP 调用以获取一卡通余额
        when (val result = makeDcpCall(ApiConstants.METHOD_GET_YKT_NUM)) { // 调用封装好的 DCP 请求方法
            is ServiceResult.Success -> {
                result.data.let { yktNum ->
                    Timber.i("获取一卡通余额成功: $yktNum")
                    ServiceResult.Success(yktNum)
                }
            }
            is ServiceResult.Error -> { // DCP 调用本身失败
                Timber.e("从 DCP 调用获取一卡通余额失败: ${result.message}")
                result // 将错误传递回去
            }
        }
    }



    /** 使用 CAS 端点加密密码。 */
    private suspend fun getEncryptedPassword(plainPassword: String): ServiceResult<String> = safeServiceCall {
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

    /** 获取 CAS 登录表单提交所需的 'lt' (Login Ticket) 值。 */
    private suspend fun getLoginLtValue(headers: Headers): ServiceResult<String> = safeServiceCall {
        Timber.d("正在从登录页面获取 LT 值: $ECJTU_LOGIN_URL")
        // 构建 GET 请求以获取登录页面 HTML
        val request = Request.Builder()
            .url(ECJTU_LOGIN_URL) // CAS 登录页面 URL
            .headers(headers) // 使用传入的通用 CAS 请求头
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
    ): ServiceResult<Unit> = safeServiceCall { // 成功时返回 Unit，抛出异常由 safeApiCall 捕获
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
                var specificErrorMsg: String? = null
                try {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        Timber.v("尝试从响应体解析错误信息...")
                        specificErrorMsg = Jsoup.parse(bodyString)
                            .select("div.mistake_notice")
                            .first()
                            ?.text()
                            ?.trim()

                        if (!specificErrorMsg.isNullOrBlank()) {
                            Timber.d("从 'div.mistake_notice' 解析到错误信息: '$specificErrorMsg'")
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
                Timber.w("登录 POST 返回 ${response.code} 但找到了 CASTGC。继续处理，但这可能不是标准流程。")
            } else if (response.isRedirect) {
                Timber.d("登录 POST 成功，收到重定向响应 (HTTP ${response.code})，且找到 CASTGC。")
            } else { // code is 200
                Timber.d("登录 POST 成功，收到 200 OK 响应，且找到 CASTGC。")
            }

        }
    }

    /**
     * 处理初始登录后必要的重定向，以建立目标服务 (如 JWXT) 的会话。
     * 应在 loginWithCredentials 成功并设置 CASTGC Cookie *之后* 调用此方法。
     */
    private suspend fun handleRedirection(headers: Headers): ServiceResult<Unit> = safeServiceCall {
        Timber.d("正在处理登录后重定向...")

        Timber.d("正在访问 JWXT 登录 URL: $JWXT_LOGIN_URL")
        val jwxtLoginRequest = Request.Builder()
            .url(JWXT_LOGIN_URL)
            .headers(headers.newBuilder()
                .removeAll("Host")
                .removeAll("Referer")
                .removeAll("Content-Type")
                .build())
            .addHeader("Host", JWXT_LOGIN_URL.toHttpUrl().host)
            .get()
            .build()

        // 使用 *跟踪重定向* 的主客户端执行请求
        client.newCall(jwxtLoginRequest).execute().use { response ->
            Timber.d("JWXT 登录 URL 访问响应代码 (重定向后最终代码): ${response.code}")
            response.headers("Set-Cookie").forEach { cookie -> Timber.v("JWXT Set-Cookie: $cookie") }
            if (!response.isSuccessful) {
                Timber.w("JWXT 登录重定向步骤的最终响应不成功 (HTTP ${response.code})。会话可能未完全建立。")
            } else {
                Timber.d("JWXT 登录重定向步骤成功完成 (HTTP ${response.code})。")
            }
        }
        delay(50)

        Timber.d("正在访问指向 JWXT 的显式 CAS 服务 URL: ${ApiConstants.ECJTU2JWXT_URL}")
        val finalRedirectRequest = Request.Builder()
            .url(ApiConstants.ECJTU2JWXT_URL)
            .headers(headers)
            .get()
            .build()

        client.newCall(finalRedirectRequest).execute().use { response ->
            Timber.d("显式 CAS->JWXT 重定向响应代码 (重定向后最终代码): ${response.code}")
            response.headers("Set-Cookie").forEach { cookie -> Timber.v("CAS->JWXT Set-Cookie: $cookie") }
            if (!response.isSuccessful) {
                Timber.w("最终 CAS->JWXT 重定向步骤的最终响应不成功 (HTTP ${response.code})。")
            } else {
                Timber.d("最终 CAS->JWXT 重定向步骤成功完成 (HTTP ${response.code})。")
            }
            // 确保响应体关闭
        }

        Timber.d("重定向处理完成。")
    }

    /**
     * 辅助函数，检查是否需要重定向 (如果存在 CASTGC 但缺少 JWXT 会话)
     * 并执行重定向。
     */
    private suspend fun handleRedirectionIfNeeded(): ServiceResult<Unit> {
        if (hasLogin(0) && !hasLogin(1)) { // 有 CAS 登录，但没有 JWXT 会话
            Timber.d("找到 CASTGC，但缺少 JWXT 会话。正在执行重定向。")
            // 准备重定向所需的 CAS 请求头
            val headers = Headers.Builder()
                .add("User-Agent", ApiConstants.USER_AGENT)
                .add("Host", ApiConstants.CAS_ECJTU_DOMAIN) // CAS 重定向 URL 的初始主机
                .build()
            return handleRedirection(headers) // 调用实际的重定向处理函数
        } else {
            Timber.d("不需要重定向或无法执行 (缺少 CASTGC 或已有 JWXT 会话)。")
            // 如果已经是 hasLogin(1) 状态，则表示成功 (无操作)
            // 如果是 !hasLogin(0) 状态，无法重定向，让 login() 处理登录流程
            // 目前，如果没有尝试重定向，则假定成功（或由调用者处理）
            return ServiceResult.Success(Unit)
        }
    }


    /** 辅助函数，用于向具有通用结构的 DCP 端点发出 POST 请求。 */
    private suspend fun makeDcpCall(
        method: String,
        params: Map<String, Any>? = null,
        currentRetries: Int = 0
    ): ServiceResult<String> = safeServiceCall(currentRetries) { // 将重试次数传递给 safeApiCall
        Timber.d("正在进行 DCP 调用: method=$method, params=$params")
        // 构建符合 DCP 预期的 JSON 请求负载结构
        val requestPayload = mapOf(
            "map" to mapOf(
                "method" to method,
                "params" to params // 如果 params 为 null，则 JSON 中不包含此键或值为 null
            ).filterValues { it != null }, // 过滤掉 null 值，如果需要
            "javaClass" to "java.util.HashMap" // DCP 通常需要的类型信息
        )
        val requestBodyString = gson.toJson(requestPayload) // 将 Map 转换为 JSON 字符串
        Timber.v("DCP 请求负载: $requestBodyString")
        // 创建 JSON 请求体
        val requestBody = requestBodyString.toRequestBody("application/json; charset=utf-8".toMediaType())

        // 构建 POST 请求
        val request = Request.Builder()
            .url(ApiConstants.DCP_SSO_URL) // DCP 处理 JSON 调用的端点
            .post(requestBody)
            // DCP 调用通常需要的请求头
            .addHeader("render", "json")
            .addHeader("clientType", "json")
            .addHeader("User-Agent", ApiConstants.USER_AGENT) // 添加用户代理
            .addHeader("Host", ApiConstants.DCP_SSO_URL.toHttpUrl().host) // 确保正确的主机
            .addHeader("Referer", ApiConstants.DCP_URL) // Referer 可能很重要，指向 DCP 基础页面
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("DCP 调用 '$method' 失败: HTTP ${response.code}")
        }
        val responseBody = response.body?.string() // 获取响应体字符串
        if (responseBody.isNullOrBlank()) {
            throw IOException("DCP 调用 '$method' 的响应体为空")
        }
        Timber.v("DCP 响应体 ($method): ${responseBody.take(500)}") // 记录响应的开头部分
        responseBody // 返回非空、非空白的响应体字符串
    }


    /**
     * API 调用的包装器，用于处理常见网络异常和重试。
     * @return [ServiceResult] ([ServiceResult.Success] 或 [ServiceResult.Error])。
     */
    private suspend fun <T> safeServiceCall(
        currentRetries: Int = 0,
        serviceCall: suspend () -> T
    ): ServiceResult<T> = withContext(Dispatchers.IO) {
        try {
            ServiceResult.Success(serviceCall())
        } catch (e: PasswordChangeException) {
            Timber.w("密码修改失败: ${e.message}")
            ServiceResult.Error(e.message ?: "密码修改操作失败", e)
        }
        catch (e: IOException) {
            Timber.w(e, "API 调用失败 (尝试次数 ${currentRetries + 1}/$maxRetries): ${e.message}")
            // 增加检查，不重试账号/密码相关的常见错误消息
            val isCredentialError = e.message?.contains("账号或密码错误") == true ||
                    e.message?.contains("旧密码错误") == true

            if (currentRetries < maxRetries - 1 && !isCredentialError) {
                Timber.d("正在重试 API 调用...")
                delay(500L * (currentRetries + 1)) // 可选：增加重试延迟
                safeServiceCall(currentRetries + 1, serviceCall)
            } else {
                ServiceResult.Error(e.message ?: "网络请求失败", e)
            }
        } catch (e: Exception) {
            Timber.e(e, "API 调用期间发生意外错误: ${e.message}")
            ServiceResult.Error("发生意外错误: ${e.message}", e)
        }
    }

    /**
     * 使用提供的凭据执行登录，并在成功时保存它们。
     * 在尝试登录前清除现有会话。
     * @param [studentId] 学号
     * @param [studentPass] 密码
     * @param [ispOption] 运营商对应id
     * @return [ServiceResult]
     */
    suspend fun loginManually(studentId: String, studentPass: String,ispOption: Int): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        // 1. 基本输入验证
        if (studentId.isBlank() || studentPass.isBlank()) {
            Timber.w("尝试使用空白凭据进行手动登录!")
            return@withContext ServiceResult.Error("账号或密码不能为空", null)
        }
        Timber.d("尝试为用户 $studentId 手动登录")

        // 2. 强制清除之前的会话 Cookie
        Timber.i("手动登录尝试前清除 Cookie。")
        cookieJar.clear()

        // 3. 调用核心登录序列函数
        val sequenceResult = performCasLoginSequence(studentId, studentPass)

        // 4. 检查序列结果
        if (sequenceResult is ServiceResult.Error) {
            Timber.e("用户 $studentId 手动登录失败 (在核心序列中): ${sequenceResult.message}")
            return@withContext sequenceResult // 返回序列错误
        }

        Timber.i("用户 $studentId 手动登录成功 (核心序列完成)。正在保存凭据 (ISP: $ispOption)。")
        try {
            prefs.saveCredentials(studentId, studentPass, ispOption)
        } catch (e: Exception) {
            Timber.e(e, "保存凭据时发生错误")
            return@withContext ServiceResult.Error("登录成功但无法保存凭据: ${e.message}")
        }

        Timber.i("用户 $studentId 的手动登录过程成功完成 (凭据已保存)")
        return@withContext ServiceResult.Success(Unit)

    }


    /** 用于更安全的 Gson JSON 解析的辅助扩展函数*/
    private fun JsonObject.getStringOrNull(key: String): String? {
        return try {
            // 检查是否存在该键，且不为 JsonNull，然后获取其字符串值
            this.get(key)?.takeIf { !it.isJsonNull }?.asString
        } catch (e: Exception) { // 处理 ClassCastException, IllegalStateException 等
            Timber.w("从 JsonObject 获取字符串 '$key' 失败: ${e.message}")
            null // 出错或找不到则返回 null
        }
    }

    private suspend fun ensureLoggedInIfNeeded(attempt: Int): Boolean {
        if (!hasLogin(1) && attempt == 1) {
            Timber.d("用户未登录，尝试自动登录")
            val loginResult = login()
            if (loginResult is ServiceResult.Error) {
                Timber.e("登录失败: $loginResult")
                return false
            }
            delay(100)
            return hasLogin(1)
        }
        return hasLogin(1)
    }
    /**
     * 可用于获取教务系统中的各页面 HTML，并返回 ServiceResult 对象，当获取失败时，会尝试自动登录并重试。
     *
     * @param [attempt] 当前尝试次数，默认为 1。
     * @param [url] 要获取的页面的 URL。
     * @param [referer] 请求的 Referer 头，默认为教务系统域名。
     * @param [params] 可选的额外请求参数，默认为 null。
     * @param [buildRequest] 可选的函数，用于构建请求，默认为 null。
     * @param [retry] 可选的函数，用于重试获取 HTML，默认为 null。
     * @return [ServiceResult] 包含获取的 HTML 或错误信息。
     */
    private suspend fun fetchHtml(
        attempt: Int = 1,
        url: HttpUrl,
        referer: String = "$JWXT_ECJTU_DOMAIN/",
        params: Map<String, String>? = null,
        buildRequest: ((HttpUrl) -> Request)? = null,
        retry: suspend (Int) -> ServiceResult<String>
    ): ServiceResult<String> {
        if (!ensureLoggedInIfNeeded(attempt)) {
            if (attempt > 1) return ServiceResult.Error("登录会话无效，且自动重登录失败")
            return ServiceResult.Error("无法建立教务系统会话，请重新登录")
        }

        try {
            val headers = Headers.Builder()
                .add("Host", url.host)
                .add("Connection", "keep-alive")
                .add("Upgrade-Insecure-Requests", "1")
                .add("User-Agent", ApiConstants.USER_AGENT)
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/*,*/*;q=0.8")
                .add("Sec-Fetch-Site", "same-origin")
                .add("Sec-Fetch-Mode", "navigate")
                .add("Sec-Fetch-User", "?1")
                .add("Sec-Fetch-Dest", "document")
                .add("Referer", referer)
                .add("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            // 添加自定义参数到 URL
            val urlWithParams = url.newBuilder().apply {
                params?.forEach { (key, value) ->
                    addQueryParameter(key, value)
                }
            }.build()

            val request = buildRequest?.invoke(urlWithParams)
                ?: Request.Builder().url(urlWithParams).headers(headers).get().build()

            val response = client.newCall(request).execute()
            response.use {
                val html = it.body?.string()
                val isLoginPage = html?.contains(JWXT_LOGIN_PAGE_IDENTIFIER) == true
                        || (it.code == 302 && it.header("Location")?.contains("login", ignoreCase = true) == true)

                if (isLoginPage) {
                    if (attempt >= maxLoginAttempts) return ServiceResult.Error("登录已过期，自动重新登录失败")
                    var loginSuccess = false
                    reLoginMutex.withLock {
                        loginSuccess = if (checkSession()) true else {
                            logout(false)
                            val result = login(forceRefresh = true)
                            result is ServiceResult.Success && checkSession()
                        }
                    }
                    return if (loginSuccess) {
                        delay(200)
                        retry(attempt + 1)
                    } else {
                        ServiceResult.Error("登录已过期，且自动重新登录失败")
                    }
                }

                return if (html.isNullOrBlank()) {
                    Timber.e("响应成功，但内容为空")
                    ServiceResult.Error("响应内容为空")
                } else {
                    ServiceResult.Success(html)
                }
            }

        } catch (e: IOException) {
            return ServiceResult.Error("网络请求失败: ${e.message}", e)
        } catch (e: Exception) {
            return ServiceResult.Error("发生未知错误: ${e.message}", e)
        }
    }


    /**
     * 获取学生成绩页面的 HTML，返回日历页面的html。
     * @param [attempt] 当前尝试次数 (用于内部重试逻辑).
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     * */
    suspend fun getStudentScoresHtml(attempt: Int = 1): ServiceResult<String> = withContext(Dispatchers.IO) {
        val url = GET_SCORE_URL.toHttpUrlOrNull()
            ?: return@withContext ServiceResult.Error("无效的教务系统域名配置")

        return@withContext fetchHtml(
            attempt = attempt,
            url = url,
            retry = ::getStudentScoresHtml
        )
    }
    /**
     * 获取素质拓展学分详情页面的原始 HTML 内容。
     *
     * @param [attempt] 当前尝试次数 (用于内部重试逻辑).
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getSecondCreditHtml(attempt: Int = 1): ServiceResult<String> = withContext(Dispatchers.IO) {
        val url = ApiConstants.GET_SECOND_CREDIT.toHttpUrlOrNull()
            ?: return@withContext ServiceResult.Error("无效的素质拓展URL")

        return@withContext fetchHtml(
            attempt = attempt,
            url = url,
            retry = ::getSecondCreditHtml
        )
    }
    /**
     * 获取学生基本信息页面的原始 HTML 内容。
     *
     * @param [attempt] 当前尝试次数 (用于内部重试逻辑).
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getStudentInfoHtml(attempt: Int = 1): ServiceResult<String> = withContext(Dispatchers.IO) {

        val url = ApiConstants.GET_STU_INFO_URL.toHttpUrlOrNull()
            ?: return@withContext ServiceResult.Error("无效的学生信息URL配置: ${ApiConstants.GET_STU_INFO_URL}")

        return@withContext fetchHtml(
            attempt = attempt,
            url = url,
            referer = "$JWXT_ECJTU_DOMAIN/index.action",
            retry = ::getStudentInfoHtml
        )
    }

    /**
     * 获取指定日期的课程表信息，返回包含日期和课程列表的原始 HTML 内容。
     *
     * @param [dateQuery] 查询日期，格式为 "YYYY-MM-DD"。如果为 null 或空，则获取当天的课表。
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getCourseScheduleHtml(dateQuery: String? = null): ServiceResult<String> = withContext(Dispatchers.IO) {
        Timber.d("获取课程表 HTML，查询日期: ${dateQuery ?: "未指定"}")

        val weiXinId = prefs.getWeiXinId()
        if (weiXinId.isBlank()) {
            return@withContext ServiceResult.Error("配置错误：缺少 weiXinID")
        }

        return@withContext safeServiceCall {
            val urlBuilder = ApiConstants.COURSE_SCHEDULE_URL.toHttpUrlOrNull()?.newBuilder()
                ?: throw IllegalArgumentException("内部错误：课程表 URL 配置无效")

            urlBuilder.addQueryParameter("weiXinID", weiXinId)
            if (!dateQuery.isNullOrBlank()) {
                urlBuilder.addQueryParameter("date", dateQuery)
            }
            val finalUrl = urlBuilder.build()

            val request = Request.Builder()
                .url(finalUrl)
                .get()
                .build()

            Timber.d("正在向 $finalUrl 发送 GET 请求获取课程表")
            val response = client.newCall(request).execute()

            response.use {
                if (!it.isSuccessful) {
                    // 抛出 IOException 会被 safeServiceCall 捕获并处理
                    throw IOException("获取课程表失败：HTTP ${it.code}")
                }

                val html = it.body?.string()
                if (html.isNullOrBlank()) {
                    // 可以认为空响应也是一种错误
                    throw IOException("响应体为空")
                }

                html
            }
        }
    }

    /**
     * 获取各学期的课程表信息，返回包含学期信息和课程表的原始 HTML 内容。
     * @param [attempt] 当前尝试次数 (用于内部重试逻辑).
     * @param [term] 指定学期，默认为空，表示获取当前学期的课程表。
     * 参数示例： "2024.1"，表示获取2024学年第一学期的课程表。
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getScheduleHtml(attempt: Int = 1,term:String? = null): ServiceResult<String> = withContext(Dispatchers.IO) {
        val url = ApiConstants.GET_SCHEDULE.toHttpUrlOrNull()
            ?: return@withContext ServiceResult.Error("无效的课程表URL配置: ${ApiConstants.GET_SCHEDULE}")

        return@withContext fetchHtml(
            attempt = attempt,
            url = url,
            params = if (term.isNullOrBlank()) null else mapOf("term" to term),
            referer = "$JWXT_ECJTU_DOMAIN/index.action",
            retry = ::getScheduleHtml
        )
    }
    /**
     * 获取各学期的课程表信息，返回包含学期信息和课程表的原始 HTML 内容。
     * @param [attempt] 当前尝试次数 (用于内部重试逻辑).
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     * */
    suspend fun getElectiveCourseHtml(attempt: Int = 1, term: String? = null): ServiceResult<String> = withContext(Dispatchers.IO) {
        val url = ApiConstants.GET_ELECTIVE_COURSE_URL.toHttpUrlOrNull()
            ?: return@withContext ServiceResult.Error("无效的选修课URL配置: ${ApiConstants.GET_ELECTIVE_COURSE_URL}")

        return@withContext fetchHtml(
            attempt = attempt,
            url = url,
            params = if (term.isNullOrBlank()) null else mapOf("term" to term),
            referer = "$JWXT_ECJTU_DOMAIN/index.action",
            retry = ::getElectiveCourseHtml
        )
    }
}