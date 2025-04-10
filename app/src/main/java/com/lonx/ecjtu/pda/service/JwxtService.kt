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
import com.lonx.ecjtu.pda.data.CourseInfo
import com.lonx.ecjtu.pda.data.DayCourses
import com.lonx.ecjtu.pda.data.LoginResult
import com.lonx.ecjtu.pda.data.PrefKeys.PASSWORD
import com.lonx.ecjtu.pda.data.PrefKeys.STUDENT_ID
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.utils.PersistentCookieJar
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
     * 尝试使用存储在 PreferencesManager 中的凭据进行登录。
     * 处理密码加密、获取 LT 值和重定向。
     * @param [forceRefresh] 是否强制刷新登录，即使已经登录。
     * @return [LoginResult] 登录结果。
     */
    suspend fun login(forceRefresh: Boolean = false): LoginResult = withContext(Dispatchers.IO) {
        val studentId = prefs.getString(STUDENT_ID, "")
        val studentPassword = prefs.getString(PASSWORD, "")
        val sessionTimeOut = checkSession()
        // 检查凭据是否存在
        if (studentId.isBlank() || studentPassword.isBlank()) {
            Timber.e("登录失败：账号为${studentId}，密码为${studentPassword}。")
            Timber.e("登录失败：PreferencesManager 中缺少凭据。")
            return@withContext LoginResult.Failure("请先设置学号和密码")
        }

        Timber.d("尝试为用户 $studentId 登录。强制刷新：$forceRefresh")

        // 检查是否需要登录
        if (!forceRefresh && hasLogin(1) && !sessionTimeOut) { // 检查完整登录状态 (包括 JWXT 会话)
            Timber.d("用户已拥有 CAS 和 JWXT 会话 Cookie。跳过登录步骤。")
            return@withContext LoginResult.Success("已登录")
        } else if (!forceRefresh && hasLogin(0)) { // 仅有 CAS 登录，可能需要重定向到 JWXT
            Timber.d("用户拥有 CAS Cookie (CASTGC)，但可能需要为 JWXT 进行重定向。")
            // 仅在需要时进行重定向，否则可能会使 CASTGC 失效
            val redirectResult = handleRedirectionIfNeeded() // 尝试处理重定向
            return@withContext if (redirectResult is ServiceResult.Success) {
                if (hasLogin(1)) { // 重定向后检查是否获取了 JWXT 会话
                    LoginResult.Success("会话已刷新")
                } else {
                    Timber.w("重定向尝试完成，但 JWXT 会话仍然缺失。")
                    LoginResult.Failure("无法建立教务系统会话")
                }
            } else {
                // 重定向失败
                LoginResult.Failure("登录重定向失败: ${(redirectResult as ServiceResult.Error).message}")
            }
        } else {
            // 需要执行完整的登录操作 (强制刷新或未登录)
            Timber.d("需要执行完整的登录操作 (刷新或未登录)。")
            if (forceRefresh) {
                Timber.i("为刷新清除 Cookie。")
                // cookieJar.clearSession() // 如果可用且合适，请使用 clearSession (通常清除会话相关的 Cookie)
                cookieJar.clear() // 清除所有 Cookie
            }

            // 1. 加密密码
            val encPassword = when (val encPasswordResult = getEncryptedPassword(studentPassword)) {
                is ServiceResult.Success -> encPasswordResult.data
                is ServiceResult.Error -> {
                    Timber.e("密码加密过程中登录失败: ${encPasswordResult.message}")
                    return@withContext LoginResult.Failure("密码加密失败: ${encPasswordResult.message}")
                }
            }

            // CAS 的通用请求头
            val headers = Headers.Builder()
                .add("User-Agent", ApiConstants.USER_AGENT)
                .add("Host", ApiConstants.CAS_ECJTU_DOMAIN) // CAS 服务器域名
                .build()

            // 2. 获取 LT 值 (Login Ticket)
            val ltValue = when (val ltValueResult = getLoginLtValue(headers)) {
                is ServiceResult.Success -> ltValueResult.data
                is ServiceResult.Error -> {
                    Timber.e("获取 LT 值过程中登录失败: ${ltValueResult.message}")
                    return@withContext LoginResult.Failure("无法获取登录令牌: ${ltValueResult.message}")
                }
            }

            // 3. 执行登录 POST 请求
            val loginResponseResult = loginWithCredentials(studentId, encPassword, ltValue, headers)
            when (loginResponseResult) {
                is ServiceResult.Success -> {
                    if (!hasLogin(0)) {
                        Timber.e("登录失败：未找到 CASTGC Cookie。可能是账号或密码错误。")
                        return@withContext LoginResult.Failure("账号或密码错误")
                    }
                    Timber.d("初始登录 POST 成功 (找到 CASTGC)，继续进行重定向。")
                }
                is ServiceResult.Error -> {
                    Timber.e("凭据提交过程中登录失败: ${loginResponseResult.message}")
                    return@withContext LoginResult.Failure(loginResponseResult.message) // 传递具体错误
                }
            }

            // 4. 处理重定向 (为目标服务如 JWXT 建立必要的会话 Cookie)
            val redirectResult = handleRedirection(headers) // 成功 POST 后强制重定向
            if (redirectResult is ServiceResult.Error) {
                Timber.e("登录部分失败：重定向错误: ${redirectResult.message}")
                // 重定向失败通常意味着无法访问目标服务（JWXT）
                return@withContext LoginResult.Failure("登录重定向失败: ${redirectResult.message}")
            }

            // 5. 最终检查 (确保 JWXT 会话已建立)
            if (!hasLogin(1)) {
                Timber.w("登录和重定向成功，但 JWXT 会话 Cookie (JSESSIONID) 缺失。")
                // 这可能表示重定向流程有问题或目标服务未正确设置会话
                return@withContext LoginResult.Failure("无法建立教务系统会话")
            }

            Timber.i("用户 $studentId 的登录过程成功完成")
            return@withContext LoginResult.Success("登录成功")
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
     * 获取学生基本信息页面的原始 HTML 内容。
     *
     * @param attempt 当前尝试次数 (用于内部重试逻辑).
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getStudentInfoHtml(attempt: Int = 1): ServiceResult<String> = withContext(Dispatchers.IO) {
        Timber.d("JwxtService: 开始获取学生信息页面 HTML... (尝试次数: $attempt)")

        if (!hasLogin(1) && attempt == 1) {
            Timber.d("JwxtService: 获取学生信息页面: 用户未登录或 JWXT 会话无效，尝试登录...")
            val loginResult = login()
            if (loginResult is LoginResult.Failure) {
                Timber.e("JwxtService: 获取学生信息页面失败：需要登录，但登录失败: ${loginResult.error}")
                return@withContext ServiceResult.Error("请先登录: ${loginResult.error}")
            }
            kotlinx.coroutines.delay(100)
            if (!hasLogin(1)) {
                Timber.e("JwxtService: 获取学生信息页面失败：登录尝试后仍然缺少 JWXT 会话。")
                return@withContext ServiceResult.Error("无法建立教务系统会话，请重新登录")
            }
            Timber.d("JwxtService: 获取学生信息页面: 初始登录成功。")
        } else if (!hasLogin(1) && attempt > 1) {
            Timber.w("JwxtService: 在第 $attempt 次尝试获取学生信息页面时，仍然未登录。")
            return@withContext ServiceResult.Error("登录会话无效，且自动重登录失败")
        }

        try {
            val infoUrl = try {
                ApiConstants.GET_STU_INFO_URL.toHttpUrl()
            } catch (e: IllegalArgumentException) {
                Timber.e("JwxtService: 无效的学生信息 URL: ${ApiConstants.GET_STU_INFO_URL}")
                return@withContext ServiceResult.Error("配置的学生信息URL无效")
            }

            val headers = Headers.Builder()
                .add("Host", infoUrl.host)
                .add("User-Agent", ApiConstants.USER_AGENT)
                .add("Referer", "$JWXT_ECJTU_DOMAIN/index.action")
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val request = Request.Builder()
                .url(infoUrl)
                .headers(headers)
                .get()
                .build()

            Timber.d("JwxtService: 正在向 ${ApiConstants.GET_STU_INFO_URL} 发送 GET 请求 (尝试 $attempt)")
            val response = client.newCall(request).execute()

            response.use {
                if (!it.isSuccessful && it.code != 302) {
                    Timber.e("JwxtService: 获取学生信息页面失败: HTTP ${it.code}")
                    throw IOException("获取学生信息页面失败: HTTP ${it.code}")
                }

                val htmlBody = it.body?.string()

                val loginPageIdentifier = JWXT_LOGIN_PAGE_IDENTIFIER
                val isLoginPage = htmlBody?.contains(loginPageIdentifier) == true ||
                        (it.code == 302 && it.header("Location")?.contains("login", ignoreCase = true) == true)

                if (isLoginPage) {
                    Timber.w("JwxtService: 获取学生信息页面: 检测到登录页 (HTTP ${it.code})，会话可能已过期。尝试次数 $attempt/$maxLoginAttempts")
                    if (attempt >= maxLoginAttempts) {
                        Timber.e("JwxtService: 获取学生信息页面: 已达到最大登录尝试次数 ($maxLoginAttempts)，失败。")
                        return@withContext ServiceResult.Error("登录已过期，自动重新登录失败")
                    }

                    var loginSuccess = false
                    reLoginMutex.withLock {
                        Timber.i("JwxtService: 获取锁 (getInfoHtml)，检测到登录页，执行强制重新登录...")
                        try {
                            if (checkSession()) {
                                Timber.i("JwxtService: 会话在等待锁期间已恢复。")
                                loginSuccess = true
                            } else {
                                logout(clearStoredCredentials = false)
                                val reLoginResult = login(forceRefresh = true)
                                if (reLoginResult is LoginResult.Success && checkSession()) {
                                    Timber.i("JwxtService: 自动重新登录成功并验证有效。")
                                    loginSuccess = true
                                } else {
                                    Timber.e("JwxtService: 自动重新登录失败或会话无效: ${(reLoginResult as? LoginResult.Failure)?.error ?: "会话验证失败"}")
                                    loginSuccess = false
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "JwxtService: 在强制重新登录过程中发生异常 (getInfoHtml)")
                            loginSuccess = false
                        }
                    }

                    if (loginSuccess) {
                        Timber.d("JwxtService: 重新登录或会话恢复成功，将重试获取学生信息页面。")
                        kotlinx.coroutines.delay(200)
                        return@withContext getStudentInfoHtml(attempt + 1) // Recursive call
                    } else {
                        return@withContext ServiceResult.Error("登录已过期，且自动重新登录失败")
                    }
                }

                if (htmlBody.isNullOrBlank()) {

                    Timber.e("JwxtService: 获取学生信息页面成功 (HTTP ${it.code})，但响应体为空。")

                    return@withContext ServiceResult.Error("获取学生信息页面成功，但响应内容为空")
                }

                Timber.i("JwxtService: 成功获取学生信息页面 HTML。")
                return@withContext ServiceResult.Success(htmlBody)

            } // response.use
        } catch (e: IOException) {
            Timber.w(e, "JwxtService: 获取学生信息页面时发生 IO 错误 (尝试 $attempt): ${e.message}")
            return@withContext ServiceResult.Error("网络请求失败: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "JwxtService: 获取学生信息页面时发生未知错误 (尝试 $attempt): ${e.message}")
            return@withContext ServiceResult.Error("发生未知错误: ${e.message}", e)
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
            if (loginResult is LoginResult.Failure) {
                Timber.e("修改密码失败：需要登录，但登录失败: ${loginResult.error}")
                return@withContext ServiceResult.Error("请先登录: ${loginResult.error}")
            }
            kotlinx.coroutines.delay(100)
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
            if (loginResult is LoginResult.Failure) {
                return@withContext ServiceResult.Error("需要登录: ${loginResult.error}")
            }
            // 短暂延迟可能有助于确保 Cookie 传播
            kotlinx.coroutines.delay(100)
        }

        // 2. (可选，但有时需要) 访问一次 DCP 基础 URL 以确保相关 Cookie (如 _WEU) 已设置
        val dcpCookieResult = safeServiceCall {
            Timber.d("正在访问 DCP URL 以确保 Cookie: ${ApiConstants.DCP_URL}")
            val request = Request.Builder().url(ApiConstants.DCP_URL).get().build()
            client.newCall(request).execute().close() // 发出请求并立即关闭响应体
            Unit // 返回 Unit 表示成功
        }
        if (dcpCookieResult is ServiceResult.Error) {
            // 记录警告，但可能仍然尝试继续，因为有时这不是必需的
            Timber.w("在调用 getYktNum 之前访问 DCP 基础 URL 失败: ${dcpCookieResult.message}")
        }

        // 3. 发出 DCP 调用以获取一卡通余额
        when (val result = makeDcpCall(ApiConstants.METHOD_GET_YKT_NUM)) { // 调用封装好的 DCP 请求方法
            is ServiceResult.Success -> {
                try {
                    // 假设响应是类似 {"map": {"balance": "12.34"}, ...} 的 JSON
                    val json = gson.fromJson(result.data, JsonObject::class.java)
                    // 安全地获取 balance 字符串
                    val balance = json.getAsJsonObject("map")?.getStringOrNull("balance")
                    // 验证余额格式是否为数字（可能带两位小数）
                    if (balance != null && balance.matches(Regex("""\d+(\.\d{1,2})?"""))) {
                        Timber.d("一卡通余额已获取: $balance")
                        ServiceResult.Success(balance) // 返回成功和余额字符串
                    } else {
                        Timber.e("从 JSON map 解析一卡通余额失败或格式无效: ${result.data}")
                        ServiceResult.Error("解析余额数据失败: 格式不符")
                    }
                } catch (e: Exception) { // 处理 JSON 解析或其他异常
                    Timber.e(e, "解析一卡通余额 JSON 失败: ${result.data}")
                    ServiceResult.Error("解析余额数据失败", e)
                }
            }
            is ServiceResult.Error -> { // DCP 调用本身失败
                Timber.e("从 DCP 调用获取一卡通余额失败: ${result.message}")
                result // 将错误传递回去
            }
        }
    }

    // --- 私有辅助函数 ---

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

        // 步骤 1：访问 JWXT 登录 URL，该 URL 应使用已存在的 CASTGC Cookie 通过 CAS 进行认证并重定向
        Timber.d("正在访问 JWXT 登录 URL: $JWXT_LOGIN_URL")
        val jwxtLoginRequest = Request.Builder()
            .url(JWXT_LOGIN_URL)
            // 使用原始请求头 (User-Agent)，Cookie 将由客户端的 CookieJar 自动添加
            // 清理此域名不需要的请求头 (如 Host, Referer, Content-Type)
            .headers(headers.newBuilder()
                .removeAll("Host")
                .removeAll("Referer")
                .removeAll("Content-Type")
                .build())
            .addHeader("Host", JWXT_LOGIN_URL.toHttpUrl().host) // 设置正确的主机为 JWXT 域名
            .get()
            .build()

        // 使用 *跟踪重定向* 的主客户端执行请求
        client.newCall(jwxtLoginRequest).execute().use { response ->
            // 我们期望它会跟踪一系列重定向（CAS -> JWXT）并最终到达 JWXT 的某个页面，
            // 在此过程中设置 JWXT 所需的会话 Cookie (如 JSESSIONID)。
            Timber.d("JWXT 登录 URL 访问响应代码 (重定向后最终代码): ${response.code}")
            // 记录最终响应设置的 Cookie
            response.headers("Set-Cookie").forEach { cookie -> Timber.v("JWXT Set-Cookie: $cookie") }
            if (!response.isSuccessful) {
                // 如果最终页面不是 200 OK，可能出了问题。
                Timber.w("JWXT 登录重定向步骤的最终响应不成功 (HTTP ${response.code})。会话可能未完全建立。")
                // 根据流程，如果在重定向期间设置了必要的 Cookie，这可能仍然可以接受。
                // 但如果最终失败，可以考虑抛出异常：
                // throw IOException("通过 CAS 建立 JWXT 会话失败: 最终 HTTP ${response.code}")
            } else {
                Timber.d("JWXT 登录重定向步骤成功完成 (HTTP ${response.code})。")
            }
            // 确保响应体关闭
        }
        // 短暂延迟可能有助于 Cookie 处理
        kotlinx.coroutines.delay(50)

        // 步骤 2：(可选，但有时是必要的) 显式访问带有 service 参数指向 JWXT 的 CAS URL。
        // 旧代码包含此步骤，它确保 CAS 为 JWXT 服务生成并验证了一个 Service Ticket (ST)，
        // 这对于确保 JWXT 正确建立会话可能至关重要。
        Timber.d("正在访问指向 JWXT 的显式 CAS 服务 URL: ${ApiConstants.ECJTU2JWXT_URL}")
        val finalRedirectRequest = Request.Builder()
            .url(ApiConstants.ECJTU2JWXT_URL) // 形如 https://cas.example.com/login?service=https://jwxt.example.com/login
            .headers(headers) // 使用 CAS 的通用请求头 (Host, User-Agent)
            .get()
            .build()

        // 同样使用跟踪重定向的主客户端
        client.newCall(finalRedirectRequest).execute().use { response ->
            Timber.d("显式 CAS->JWXT 重定向响应代码 (重定向后最终代码): ${response.code}")
            response.headers("Set-Cookie").forEach { cookie -> Timber.v("CAS->JWXT Set-Cookie: $cookie") }
            if (!response.isSuccessful) {
                Timber.w("最终 CAS->JWXT 重定向步骤的最终响应不成功 (HTTP ${response.code})。")
                // 如果上一步没有完全成功，这一步的失败可能更关键。
                // throw IOException("最终 CAS->JWXT 重定向步骤失败: 最终 HTTP ${response.code}")
            } else {
                Timber.d("最终 CAS->JWXT 重定向步骤成功完成 (HTTP ${response.code})。")
            }
            // 确保响应体关闭
        }

        Timber.d("重定向处理完成。")
        // 如果没有抛出异常则表示成功
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
        method: String,                  // DCP 方法名 (例如 "getYktNum")
        params: Map<String, Any>? = null, // 方法参数 (如果需要)
        currentRetries: Int = 0          // 当前重试次数 (由 safeServiceCall 管理)
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
                kotlinx.coroutines.delay(500L * (currentRetries + 1)) // 可选：增加重试延迟
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
        cookieJar.clear() // 清除所有 Cookie

        // 3. 加密密码
        val encPassword = when (val encPasswordResult = getEncryptedPassword(studentPass)) {
            is ServiceResult.Success -> encPasswordResult.data
            is ServiceResult.Error -> {
                Timber.e("手动登录在密码加密过程中失败: ${encPasswordResult.message}")
                return@withContext ServiceResult.Error("密码加密失败: ${encPasswordResult.message}")
            }
        }

        // CAS 的通用请求头
        val headers = Headers.Builder()
            .add("User-Agent", ApiConstants.USER_AGENT)
            .add("Host", ApiConstants.CAS_ECJTU_DOMAIN)
            .build()

        // 4. 获取 LT 值
        val ltValue = when (val ltValueResult = getLoginLtValue(headers)) {
            is ServiceResult.Success -> ltValueResult.data
            is ServiceResult.Error -> {
                Timber.e("手动登录在获取 LT 值过程中失败: ${ltValueResult.message}")
                return@withContext ServiceResult.Error("无法获取登录令牌: ${ltValueResult.message}")
            }
        }

        // 5. 执行登录 POST 请求
        when (val loginResponseResult = loginWithCredentials(studentId, encPassword, ltValue, headers)) {
            is ServiceResult.Success -> {
                // 检查 CASTGC 是否确实已设置
                if (!hasLogin(0)) { // Type 0 for CASTGC
                    Timber.e("手动登录失败：未找到 CASTGC Cookie，请检查凭据。")
                    // 返回更具体的错误可能更好，但取决于 loginWithCredentials 是否能区分
                    return@withContext ServiceResult.Error("账号或密码错误")
                }
                Timber.d("初始手动登录 POST 成功 (找到 CASTGC)，继续进行重定向。")
            }
            is ServiceResult.Error -> {
                Timber.e("手动登录在凭据提交过程中失败: ${loginResponseResult.message}")
                // 传递来自 loginWithCredentials 的具体错误消息
                return@withContext ServiceResult.Error(loginResponseResult.message)
            }
        }

        // 6. 处理重定向
        when (val redirectResult = handleRedirection(headers)) {
            is ServiceResult.Success -> {
                Timber.d("重定向处理成功。")
            }
            is ServiceResult.Error -> {
                Timber.e("手动登录部分失败：重定向错误: ${redirectResult.message}")
                // 如果重定向失败，则不保存凭据，并返回失败
                return@withContext ServiceResult.Error("登录重定向失败: ${redirectResult.message}")
            }
        }

        // 7. 最终检查 (JWXT 会话 - JSESSIONID)
        if (!hasLogin(1)) { // Type 1 for JSESSIONID (assuming)
            Timber.w("手动登录完成，但 JWXT 会话 Cookie (JSESSIONID) 可能缺失。")
            return@withContext ServiceResult.Error("无法建立教务系统会话")
        }

        // --- 重要：完全成功后保存凭据 ---
        Timber.i("用户 $studentId 手动登录成功。正在保存凭据 (ISP: $ispOption)。")
        try {
            prefs.saveCredentials(studentId, studentPass, ispOption)
        } catch (e: Exception) {
            Timber.e(e, "保存凭据时发生错误！")

            return@withContext ServiceResult.Error("登录成功但无法保存凭据: ${e.message}")
        }

        Timber.i("用户 $studentId 的手动登录过程成功完成")
        return@withContext ServiceResult.Success(Unit)

    }


    // --- 工具扩展/类 ---
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

    // 用于 HTML 或数据映射期间解析错误的自定义异常
    class ParseException(message: String): IOException(message)


    /** 通用 GET 请求辅助函数 (请谨慎使用，注意认证状态)。 */
    suspend fun getRaw(url: String, params: Map<String, String>? = null, headers: Headers? = null): Response {
        Timber.d("通用 GET: $url")
        // 如果调用 *requiresLogin*，考虑在此处添加检查，并在 !hasLogin(X) 时失败
        // 例如: if (requiresLogin && !hasLogin(1)) { throw IOException("GET $url 需要登录") }

        // 验证并构建 URL
        val httpUrl = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("无效 URL: $url")
        val urlBuilder = httpUrl.newBuilder()
        // 添加查询参数
        params?.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }

        val requestBuilder = Request.Builder().url(urlBuilder.build())
        // 如果提供了 headers，则使用它们替换默认的 (OkHttp 默认会添加一些)
        headers?.let { requestBuilder.headers(it) }

        // 在 IO 线程执行网络请求
        return withContext(Dispatchers.IO) {
            client.newCall(requestBuilder.build()).execute()
        }
    }

    /** 通用 POST 请求辅助函数 (谨慎使用，注意认证状态)。 */
    suspend fun postRaw(url: String, formData: Map<String, String>? = null, headers: Headers? = null): Response {
        Timber.d("通用 POST: $url")
        // 考虑添加登录检查
        // if (requiresLogin && !hasLogin(1)) { throw IOException("POST $url 需要登录") }

        // 构建表单体
        val bodyBuilder = FormBody.Builder()
        formData?.forEach { (key, value) -> bodyBuilder.add(key, value) }

        // 构建请求
        val requestBuilder = Request.Builder()
            .url(url.toHttpUrlOrNull() ?: throw IllegalArgumentException("无效 URL: $url"))
            .post(bodyBuilder.build())
        // 如果提供了 headers，则使用它们
        headers?.let { requestBuilder.headers(it) }

        // 在 IO 线程执行
        return withContext(Dispatchers.IO) {
            client.newCall(requestBuilder.build()).execute()
        }
    }

    /**
     * 获取指定日期的课程表信息，返回包含日期和课程列表的 DayCourses 对象。
     *
     * @param [dateQuery] 查询日期，格式为 "YYYY-MM-DD"。如果为 null 或空，则获取当天的课表。
     * @return [ServiceResult] 包含 [DayCourses] 或错误信息。
     */
    suspend fun getCourseSchedule(dateQuery: String? = null): ServiceResult<DayCourses> = withContext(Dispatchers.IO) {
        Timber.e("开始获取课程表信息... 查询日期: ${dateQuery ?: "未指定"}")

        val weiXinId = prefs.getWeiXinId()
        if (weiXinId.isBlank()) {
            Timber.e("警告：无法从持久化存储中获取 weiXinID。将尝试不带此参数请求课表。")
            return@withContext ServiceResult.Error("配置错误：缺少 weiXinID")
        }

        try {
            val urlBuilder = ApiConstants.COURSE_SCHEDULE_URL.toHttpUrlOrNull()?.newBuilder()
                ?: return@withContext ServiceResult.Error("内部错误：课程表 URL 配置无效")

            if (weiXinId.isNotBlank()) {
                urlBuilder.addQueryParameter("weiXinID", weiXinId)
            }
            if (!dateQuery.isNullOrBlank()) {
                urlBuilder.addQueryParameter("date", dateQuery)
            }
            val targetUrl = urlBuilder.build()


            val request = Request.Builder()
                .url(targetUrl)
                .get()
                .build()

            Timber.d("正在向 $targetUrl 发送 GET 请求获取课程表")

            val response = client.newCall(request).execute()

            response.use { it ->
                if (!it.isSuccessful) {
                    Timber.e("获取课程表页面失败: HTTP ${it.code}")
                    throw IOException("获取课程表页面失败: HTTP ${it.code}")
                }

                val htmlBody = it.body?.string()
                if (htmlBody.isNullOrBlank()) {
                    Timber.e("获取课程表页面成功，但响应体为空。")
                    throw ParseException("课程表页面响应体为空")
                }

                Timber.d("获取课程表页面成功，开始解析 HTML...")
                val document = Jsoup.parse(htmlBody)

                val extractedDate = document.selectFirst("div.center")?.text()?.trim()
                val scheduleDate = if (extractedDate.isNullOrBlank()) {
                    Timber.e("无法从 'div.center' 解析日期，将使用默认值。")
                    "日期未知"
                } else {
                    extractedDate
                }
                Timber.e("解析得到的日期: $scheduleDate")

                val courseListElement = document.selectFirst("div.calendar ul.rl_info")

                if (courseListElement == null) {
                    Timber.e("无法在 HTML 中找到课程列表容器 (div.calendar ul.rl_info)。页面结构可能已更改。返回带日期的空课表。")
                    return@withContext ServiceResult.Success(DayCourses(scheduleDate, emptyList()))
                }

                val hasImagePlaceholder = courseListElement.selectFirst("li > p > img") != null
                if (hasImagePlaceholder) {
                    Timber.e("检测到图片占位符，表示当天无课。返回带日期的空课表。")
                    return@withContext ServiceResult.Success(DayCourses(scheduleDate, emptyList()))
                }

                val courses = mutableListOf<CourseInfo>()
                val listItems = courseListElement.select("li")

                if (listItems.isEmpty()) {
                    Timber.i("课程列表容器存在，但未找到 'li' 元素（且非图片占位符），表示当天无课。返回带日期的空课表。")
                    return@withContext ServiceResult.Success(DayCourses(scheduleDate, emptyList()))
                }

                Timber.e("找到 ${listItems.size} 个课程条目，开始解析...")
                for (item in listItems) {
                    try {
                        val pElement = item.selectFirst("p")
                        if (pElement == null) {
                            Timber.w("跳过一个 'li' 条目，因为它不包含 'p' 标签。")
                            continue
                        }
                        val contentNodes = pElement.childNodes()
                        val textParts = mutableListOf<String>()
                        contentNodes.forEach { node ->
                            if (node is org.jsoup.nodes.TextNode) {
                                val text = node.text().trim()
                                if (text.isNotEmpty()) {
                                    textParts.add(text)
                                }
                            }
                        }

                        val courseName = textParts.getOrNull(0) ?: "N/A"

                        val rawTimeInfo = textParts.getOrNull(1) ?: ""
                        val timeParts = rawTimeInfo.substringAfter("时间：", "").trim().split(' ', limit = 2)
                        val courseWeek = if (timeParts.isNotEmpty()) timeParts[0].trim().takeIf { it.isNotEmpty() } ?: "N/A" else "N/A"
                        val courseTime = if (timeParts.size > 1) timeParts[1].trim().takeIf { it.isNotEmpty() } ?: "N/A" else "N/A"


                        val courseLocation = textParts.getOrNull(2)?.substringAfter("地点：", "")?.trim()?.takeIf { it.isNotEmpty() } ?: "N/A"

                        val courseTeacher = textParts.getOrNull(3)?.substringAfter("教师：", "")?.trim()?.takeIf { it.isNotEmpty() } ?: "N/A"


                        if (courseName != "N/A") {
                            courses.add(
                                CourseInfo(
                                    courseName = courseName,
                                    courseTime = "节次：${courseTime}",
                                    courseWeek = "上课周：${courseWeek}",
                                    courseLocation = "地点：${courseLocation}",
                                    courseTeacher = "教师：${courseTeacher}"
                                )
                            )
                        } else {
                            Timber.e("跳过一个条目，因为未能解析出有效的课程名称: ${pElement.text()}")
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "解析单个课程条目时出错: ${item.html()}")
                        continue
                    }
                }

                Timber.i("课程表解析成功，日期: $scheduleDate, 课程数: ${courses.size}")
                return@withContext ServiceResult.Success(DayCourses(scheduleDate, courses))

            }
        } catch (e: IOException) {
            Timber.e(e, "获取课程表时发生网络或IO错误: ${e.message}")
            return@withContext ServiceResult.Error("网络请求失败: ${e.message}", e)
        } catch (e: ParseException) {
            Timber.e(e, "解析课程表HTML时出错: ${e.message}")
            return@withContext ServiceResult.Error("数据解析失败: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "获取课程表时发生未知错误: ${e.message}")
            return@withContext ServiceResult.Error("发生未知错误: ${e.message}", e)
        }
    }
    suspend fun getStudentScoresHtml(attempt: Int = 1): ServiceResult<String> = withContext(Dispatchers.IO) {
        Timber.d("开始获取成绩页面 HTML... (尝试次数: $attempt)")

        // --- 1. 初始登录状态检查 ---
        // 与 getStuInfo 类似，如果完全未登录，则先尝试登录
        if (!hasLogin(1) && attempt == 1) {
            Timber.d("获取成绩页面: 用户未登录或 JWXT 会话无效，尝试登录...")
            val loginResult = login()
            if (loginResult is LoginResult.Failure) {
                Timber.e("获取成绩页面失败：需要登录，但登录失败: ${loginResult.error}")
                return@withContext ServiceResult.Error("请先登录: ${loginResult.error}")
            }
            kotlinx.coroutines.delay(100) // 短暂延迟，让 Cookie 生效
            if (!hasLogin(1)) {
                Timber.e("获取成绩页面失败：登录尝试后仍然缺少 JWXT 会话。")
                return@withContext ServiceResult.Error("无法建立教务系统会话，请重新登录")
            }
            Timber.d("获取成绩页面: 初始登录成功。")
        } else if (!hasLogin(1) && attempt > 1) {
            // 如果在重试时仍然没有登录，说明之前的自动重登录失败了
            Timber.w("在第 $attempt 次尝试获取成绩页面时，仍然未登录。")
            return@withContext ServiceResult.Error("登录会话无效，且自动重登录失败")
        } else {
            Timber.d("获取成绩页面: 用户已登录或正在进行重试。")
        }

        try {
            // 构建 URL
            val urlBuilder = GET_SCORE_URL.toHttpUrlOrNull()?.newBuilder()
                ?: return@withContext ServiceResult.Error("无效的教务系统域名配置")
            val url = urlBuilder
                .build()

            Timber.d("目标 URL: $url")

            val headers = Headers.Builder()
                .add("Host", url.host)
                .add("Connection", "keep-alive")
                .add("Upgrade-Insecure-Requests", "1")
                .add("User-Agent", ApiConstants.USER_AGENT)
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .add("Sec-Fetch-Site", "same-origin")
                .add("Sec-Fetch-Mode", "navigate")
                .add("Sec-Fetch-User", "?1")
                .add("Sec-Fetch-Dest", "document")
                .add("Referer", "$JWXT_ECJTU_DOMAIN/")
                .add("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            // 构建请求
            val request = Request.Builder()
                .url(GET_SCORE_URL)
                .headers(headers)
                .get()
                .build()

            Timber.d("正在向 $url 发送 GET 请求 (尝试 $attempt)")
            val response = client.newCall(request).execute()

            response.use {
                if (!it.isSuccessful && it.code != 302) {
                    Timber.e("获取成绩页面失败: HTTP ${it.code}")
                    throw IOException("获取成绩页面失败: HTTP ${it.code}")
                }

                // 读取响应体内容
                val htmlBody = it.body?.string()

                // --- 3. 检查是否返回了登录页面 (会话过期) ---
                val isLoginPage = htmlBody?.contains(JWXT_LOGIN_PAGE_IDENTIFIER) == true ||
                        (it.code == 302 && it.header("Location")?.contains("login", ignoreCase = true) == true)


                if (isLoginPage) {
                    Timber.w("获取成绩页面: 检测到返回的是登录页面或重定向到登录页 (HTTP ${it.code})，会话可能已过期。尝试次数 $attempt/$maxLoginAttempts")

                    // 如果尝试次数已达上限，则失败
                    if (attempt >= maxLoginAttempts) {
                        Timber.e("获取成绩页面: 已达到最大登录尝试次数 ($maxLoginAttempts)，失败。")
                        return@withContext ServiceResult.Error("登录已过期，自动重新登录失败")
                    }

                    // --- 触发重新登录逻辑 ---
                    var loginSuccess = false
                    // 使用互斥锁确保只有一个线程执行登录操作
                    reLoginMutex.withLock {
                        Timber.i("获取锁 (getScoresHtml)，检测到登录页，执行强制重新登录...")
                        try {
                            if (checkSession()) {
                                Timber.i("会话在等待锁期间已由另一线程恢复。跳过重新登录。")
                                loginSuccess = true
                            } else {
                                logout(clearStoredCredentials = false) // 清理旧 Cookie，保留凭据
                                val reLoginResult = login(forceRefresh = true) // 强制刷新登录
                                if (reLoginResult is LoginResult.Success && checkSession()) { // 再次验证
                                    Timber.i("自动重新登录成功并验证有效。")
                                    loginSuccess = true
                                } else {
                                    Timber.e("自动重新登录失败或会话无效: ${(reLoginResult as? LoginResult.Failure)?.error ?: "会话验证失败"}")
                                    loginSuccess = false
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "在强制重新登录过程中发生异常 (getScoresHtml)")
                            loginSuccess = false
                        }
                    }

                    // 根据登录结果决定下一步
                    if (loginSuccess) {
                        Timber.d("重新登录或会话恢复成功，将重试获取成绩页面。")
                        kotlinx.coroutines.delay(200) // 短暂延迟后重试
                        // ***递归调用进行重试***
                        return@withContext getStudentScoresHtml(attempt + 1) // 传递增加后的尝试次数
                    } else {
                        // 重新登录失败，返回错误
                        return@withContext ServiceResult.Error("登录已过期，且自动重新登录失败")
                    }
                }

                // --- 4. 如果不是登录页面，返回 HTML ---
                if (htmlBody.isNullOrBlank()) {
                    // If response was successful but body is empty/null (and wasn't login page)
                    Timber.e("获取成绩页面成功 (HTTP ${it.code})，但响应体为空。")
                    // Log headers or other info for debugging if needed
                    Timber.v("Response Headers: ${it.headers}")
                    return@withContext ServiceResult.Error("获取成绩页面成功，但响应内容为空")
                }

                Timber.i("成功获取成绩页面 HTML")
                return@withContext ServiceResult.Success(htmlBody)
            } // response.use 结束

        } catch (e: IOException) {
            // 处理网络IO错误或之前抛出的非成功HTTP代码错误
            Timber.w(e, "获取成绩页面时发生 IO 错误 (尝试 $attempt): ${e.message}")
            // 这里可以添加针对网络抖动的重试逻辑，但这通常由 safeServiceCall 处理，
            // 但由于我们需要自定义登录重试，所以直接在这里处理或返回错误
            return@withContext ServiceResult.Error("网络请求失败: ${e.message}", e)
        } catch (e: Exception) {
            // 处理其他意外错误
            Timber.e(e, "获取成绩页面时发生未知错误 (尝试 $attempt): ${e.message}")
            return@withContext ServiceResult.Error("发生未知错误: ${e.message}", e)
        }
    }
    /**
     * 获取素质拓展学分详情页面的原始 HTML 内容。
     * Handles session checking and automatic re-login attempts internally.
     *
     * @param attempt 当前尝试次数 (用于内部重试逻辑).
     * @return ServiceResult 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getSecondCreditHtml(attempt: Int = 1): ServiceResult<String> = withContext(Dispatchers.IO) {
        val targetUrlString = ApiConstants.GET_SECOND_CREDIT
        Timber.d("JwxtService: 开始获取素质拓展学分页面 HTML ($targetUrlString)... (尝试次数: $attempt)")

        if (!hasLogin(1) && attempt == 1) {
            Timber.d("JwxtService: 获取素质拓展学分页面: 用户未登录或 JWXT 会话无效，尝试登录...")
            val loginResult = login()
            if (loginResult is LoginResult.Failure) {
                Timber.e("JwxtService: 获取素质拓展页面失败：需要登录，但登录失败: ${loginResult.error}")
                return@withContext ServiceResult.Error("请先登录: ${loginResult.error}")
            }
            kotlinx.coroutines.delay(100)
            if (!hasLogin(1)) {
                Timber.e("JwxtService: 获取素质拓展页面失败：登录尝试后仍然缺少 JWXT 会话。")
                return@withContext ServiceResult.Error("无法建立教务系统会话，请重新登录")
            }
            Timber.d("JwxtService: 获取素质拓展页面: 初始登录成功。")
        } else if (!hasLogin(1) && attempt > 1) {
            Timber.w("JwxtService: 在第 $attempt 次尝试获取素质拓展页面时，仍然未登录。")
            return@withContext ServiceResult.Error("登录会话无效，且自动重登录失败")
        }

        try {
            val url = targetUrlString.toHttpUrlOrNull()
                ?: return@withContext ServiceResult.Error("无效的素质拓展学分URL配置: $targetUrlString")

            Timber.d("JwxtService: 目标 URL: $url")

            val headers = Headers.Builder()
                .add("Host", url.host)
                .add("Connection", "keep-alive")
                .add("Upgrade-Insecure-Requests", "1")
                .add("User-Agent", ApiConstants.USER_AGENT)
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .add("Sec-Fetch-Site", "same-origin")
                .add("Sec-Fetch-Mode", "navigate")
                .add("Sec-Fetch-Dest", "document")
                .add("Referer", "$JWXT_ECJTU_DOMAIN/")
                .add("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            val request = Request.Builder()
                .url(url)
                .headers(headers)
                .get()
                .build()

            Timber.d("JwxtService: 正在向 $url 发送 GET 请求 (尝试 $attempt)")
            val response = client.newCall(request).execute()

            response.use {
                if (!it.isSuccessful && it.code != 302) {
                    Timber.e("JwxtService: 获取素质拓展页面失败: HTTP ${it.code}")
                    throw IOException("获取素质拓展页面失败: HTTP ${it.code}")
                }

                val htmlBody = it.body?.string()
                val isLoginPage = htmlBody?.contains(JWXT_LOGIN_PAGE_IDENTIFIER) == true ||
                        (it.code == 302 && it.header("Location")?.contains("login", ignoreCase = true) == true)

                if (isLoginPage) {
                    Timber.w("JwxtService: 获取素质拓展页面: 检测到登录页 (HTTP ${it.code})，会话可能已过期。尝试次数 $attempt/$maxLoginAttempts")
                    if (attempt >= maxLoginAttempts) {
                        Timber.e("JwxtService: 获取素质拓展页面: 已达到最大登录尝试次数 ($maxLoginAttempts)，失败。")
                        return@withContext ServiceResult.Error("登录已过期，自动重新登录失败")
                    }

                    var loginSuccess = false
                    reLoginMutex.withLock {
                        Timber.i("JwxtService: 获取锁 (getExtracurricularHtml)，检测到登录页，执行强制重新登录...")
                        try {
                            if (checkSession()) {
                                Timber.i("JwxtService: 会话在等待锁期间已恢复。")
                                loginSuccess = true
                            } else {
                                logout(clearStoredCredentials = false)
                                val reLoginResult = login(forceRefresh = true)
                                if (reLoginResult is LoginResult.Success && checkSession()) {
                                    Timber.i("JwxtService: 自动重新登录成功并验证有效。")
                                    loginSuccess = true
                                } else {
                                    Timber.e("JwxtService: 自动重新登录失败或会话无效: ${(reLoginResult as? LoginResult.Failure)?.error ?: "会话验证失败"}")
                                    loginSuccess = false
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "JwxtService: 在强制重新登录过程中发生异常 (getExtracurricularHtml)")
                            loginSuccess = false
                        }
                    }

                    if (loginSuccess) {
                        Timber.d("JwxtService: 重新登录或会话恢复成功，将重试获取素质拓展页面。")
                        kotlinx.coroutines.delay(200)
                        return@withContext getSecondCreditHtml(attempt + 1)
                    } else {
                        return@withContext ServiceResult.Error("登录已过期，且自动重新登录失败")
                    }
                }

                if (htmlBody.isNullOrBlank()) {
                    Timber.e("JwxtService: 获取素质拓展页面成功 (HTTP ${it.code})，但响应体为空。")
                    return@withContext ServiceResult.Error("获取素质拓展页面成功，但响应内容为空")
                }

                Timber.i("JwxtService: 成功获取素质拓展页面 HTML。")
                return@withContext ServiceResult.Success(htmlBody)
            }

        } catch (e: IOException) {
            Timber.w(e, "JwxtService: 获取素质拓展页面时发生 IO 错误 (尝试 $attempt): ${e.message}")
            return@withContext ServiceResult.Error("网络请求失败: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "JwxtService: 获取素质拓展页面时发生未知错误 (尝试 $attempt): ${e.message}")
            return@withContext ServiceResult.Error("发生未知错误: ${e.message}", e)
        }
    }

}