package com.lonx.ecjtu.pda.service

// ... (其他 import 和类定义保持不变)
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
import com.lonx.ecjtu.pda.data.PrefKeys.ISP
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
import java.net.SocketTimeoutException


// 自定义异常，用于明确表示会话已过期（检测到登录页）
class SessionExpiredException(message: String = "Session expired, detected login page.") : IOException(message)
// 自定义异常，用于解析数据时的错误
class ParseException(message: String): IOException(message)

// 用于 hasLogin 的会话类型枚举，提高可读性
private enum class SessionType {
    CAS, // 仅检查 CAS (CASTGC)
    JWXT // 检查 CAS 和 JWXT (JSESSIONID)
}

class JwxtService(
    private val prefs: PreferencesManager, // 用于存储和读取设置（如学号密码）
    private val cookieJar: PersistentCookieJar,         // 用于持久化存储 Cookie
    private val client: OkHttpClient                     // 用于执行网络请求
): BaseService {

    private val maxLoginAttempts = 2 // 最大自动重登录尝试次数
    private val gson = Gson()  // 用于 JSON 解析
    private val reLoginMutex = Mutex() // 用于防止并发重登录

    init {
        Timber.d("JwxtService 初始化完成。")
    }

    // --- 公共 API 方法 ---

    /**
     * 确保用户拥有有效的 JWXT 会话。
     * 如果会话无效，会尝试使用存储的凭据自动登录。
     *
     * @param forceRefresh 如果为 true，则强制执行完整的登录流程，即使当前会话看起来有效。
     * @return [ServiceResult<Unit>] 指示是否成功建立或确认了有效会话。
     */
    suspend fun ensureLogin(forceRefresh: Boolean = false): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        Timber.d("开始确保登录状态... 强制刷新: $forceRefresh")

        // 如果强制刷新，先清除 Cookie
        if (forceRefresh) {
            Timber.i("强制刷新：清除现有 Cookie。")
            cookieJar.clear()
        }

        // 检查 JWXT 会话是否已存在且有效
        if (!forceRefresh && hasLogin(SessionType.JWXT) && checkJwxtSessionOnline()) {
            Timber.i("用户已拥有有效的 JWXT 会话。跳过登录。")
            return@withContext ServiceResult.Success(Unit)
        }

        // 检查是否只有 CAS 会话，尝试通过重定向获取 JWXT 会话
        if (!forceRefresh && hasLogin(SessionType.CAS) && !hasLogin(SessionType.JWXT)) {
            Timber.i("拥有 CAS Cookie，但缺少 JWXT 会话。尝试处理重定向...")
            val redirectResult = handleRedirectionIfNeeded()
            // 检查重定向后 JWXT 会话是否建立成功
            if (redirectResult is ServiceResult.Success && hasLogin(SessionType.JWXT) && checkJwxtSessionOnline()) {
                Timber.i("通过重定向成功刷新/建立了 JWXT 会话。")
                return@withContext ServiceResult.Success(Unit)
            } else {
                val errorMsg = (redirectResult as? ServiceResult.Error)?.message ?: "重定向后 JWXT 会话仍无效"
                Timber.w("重定向尝试失败或完成后 JWXT 会话仍然缺失 ($errorMsg)。将继续尝试完整登录。")
                // 清除可能不完整的 Cookie 状态
                // logout(false) // 考虑是否需要清除，或者让 performFullLogin 处理
            }
        }

        // 执行完整登录流程
        Timber.i("需要执行完整登录流程。")
        // 注意：performFullLogin 返回的是 ServiceResult<Unit>
        return@withContext performFullLogin()
    }

    /**
     * 使用提供的凭据执行手动登录。
     * 会清除之前的会话并保存新凭据（如果登录成功）。
     * @param studentId 学号
     * @param studentPass 密码
     * @param ispOption 运营商选项 (注意: 这个参数在原始的登录逻辑中没有被使用，这里保留以匹配原始签名，但实际登录过程未使用)
     * @return [ServiceResult<Unit>] 登录结果
     */
    suspend fun loginManually(studentId: String, studentPass: String, ispOption: Int): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        if (studentId.isBlank() || studentPass.isBlank()) {
            Timber.w("尝试使用空白凭据进行手动登录!")
            return@withContext ServiceResult.Error("账号或密码不能为空")
        }
        Timber.i("尝试为用户 $studentId 手动登录...")

        // 清除旧会话
        logout(clearStoredCredentials = false) // 不清除存储的凭据，因为我们要用新的覆盖

        // 执行核心登录逻辑
        val loginResult = performFullLogin(studentId, studentPass)

        return@withContext when (loginResult) {
            is ServiceResult.Success -> {
                Timber.i("用户 $studentId 手动登录成功。正在保存凭据 (ISP: $ispOption)。")
                try {
                    prefs.saveCredentials(studentId, studentPass, ispOption)
                    ServiceResult.Success(Unit)
                } catch (e: Exception) {
                    Timber.e(e, "保存凭据时发生错误！")
                    // 登录本身成功了，但保存失败了，这算是一个部分成功，但对用户来说可能需要提示
                    ServiceResult.Error("登录成功但无法保存凭据: ${e.message}", e)
                }
            }
            is ServiceResult.Error -> {
                Timber.e("用户 $studentId 手动登录失败: ${loginResult.message}")
                loginResult // 返回登录失败的错误
            }
        }
    }


    /**
     * 执行退出登录操作。
     * 清除网络会话 (Cookies) 和可选地清除存储的凭据。
     * @param clearStoredCredentials 是否同时清除 PreferencesManager 中保存的学号密码，默认为 true。
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
     * 获取学生基本信息页面的原始 HTML 内容。
     * 会自动处理会话检查和重登录。
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getStudentInfoHtml(): ServiceResult<String> = executeJwxtRequestWithRetry {
        Timber.d("执行实际获取学生信息的操作...")
        val infoUrl = ApiConstants.GET_STU_INFO_URL.toHttpUrlOrNull()
            ?: return@executeJwxtRequestWithRetry ServiceResult.Error("配置的学生信息URL无效") // 直接返回错误

        val headers = buildJwxtHeaders(infoUrl.host, "$JWXT_ECJTU_DOMAIN/index.action")
        val request = Request.Builder().url(infoUrl).headers(headers).get().build()

        Timber.d("正在向 ${infoUrl} 发送 GET 请求")
        val response = client.newCall(request).execute()

        // 调用 handleJwxtResponse，如果成功，将其结果包装在 ServiceResult.Success 中返回
        // 如果 handleJwxtResponse 抛出异常，executeJwxtRequestWithRetry 会捕获并返回 Error
        val html = handleJwxtResponse(response, "获取学生信息页面")
        ServiceResult.Success(html) // *** 修改点：包装为 Success ***
    }

    /**
     * 获取学生成绩页面的原始 HTML 内容。
     * 会自动处理会话检查和重登录。
     * @return [ServiceResult] 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getStudentScoresHtml(): ServiceResult<String> = executeJwxtRequestWithRetry {
        Timber.d("执行实际获取成绩页面的操作...")
        val scoreUrl = ApiConstants.GET_SCORE_URL.toHttpUrlOrNull()
            ?: return@executeJwxtRequestWithRetry ServiceResult.Error("配置的成绩查询URL无效")

        val headers = buildJwxtHeaders(scoreUrl.host, "$JWXT_ECJTU_DOMAIN/") // Referer 可能需要调整
        val request = Request.Builder().url(scoreUrl).headers(headers).get().build()

        Timber.d("正在向 $scoreUrl 发送 GET 请求")
        val response = client.newCall(request).execute()

        // 调用 handleJwxtResponse 并包装结果
        val html = handleJwxtResponse(response, "获取成绩页面")
        ServiceResult.Success(html) // *** 修改点：包装为 Success ***
    }

    /**
     * 获取素质拓展学分详情页面的原始 HTML 内容。
     * 会自动处理会话检查和重登录。
     * @return ServiceResult 包含成功获取的 HTML 字符串或错误信息.
     */
    suspend fun getSecondCreditHtml(): ServiceResult<String> = executeJwxtRequestWithRetry {
        Timber.d("执行实际获取素质拓展学分的操作...")
        val creditUrl = ApiConstants.GET_SECOND_CREDIT.toHttpUrlOrNull()
            ?: return@executeJwxtRequestWithRetry ServiceResult.Error("配置的素质拓展学分URL无效")

        val headers = buildJwxtHeaders(creditUrl.host, "$JWXT_ECJTU_DOMAIN/") // Referer 可能需要调整
        val request = Request.Builder().url(creditUrl).headers(headers).get().build()

        Timber.d("正在向 $creditUrl 发送 GET 请求")
        val response = client.newCall(request).execute()

        // 调用 handleJwxtResponse 并包装结果
        val html = handleJwxtResponse(response, "获取素质拓展学分页面")
        ServiceResult.Success(html) // *** 修改点：包装为 Success ***
    }

    /**
     * 修改密码。
     * 会自动处理会话检查和重登录。
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return [ServiceResult] 修改密码结果，成功时返回 Unit。
     */
    suspend fun updatePassword(oldPassword: String, newPassword: String): ServiceResult<Unit> = executeJwxtRequestWithRetry {
        Timber.d("执行实际修改密码的操作...")
        if (newPassword.isBlank()) {
            Timber.w("修改密码失败：新密码不能为空。")
            return@executeJwxtRequestWithRetry ServiceResult.Error("新密码不能为空")
        }

        val updatePasswordUrl = ApiConstants.UPDATE_PASSWORD.toHttpUrlOrNull()
            ?: return@executeJwxtRequestWithRetry ServiceResult.Error("配置的修改密码URL无效")

        val formBody = FormBody.Builder()
            .add("oldPassword", oldPassword)
            .add("password", newPassword)
            .add("rpassword", newPassword)
            .build()

        val headers = buildJwxtHeaders(
            host = updatePasswordUrl.host,
            referer = ApiConstants.GET_STU_INFO_URL, // 通常修改密码是从信息页或特定设置页发起的
            extraHeaders = mapOf(
                "Accept" to "*/*", // API 接口通常接受任意类型
                "Origin" to "https://${JWXT_ECJTU_DOMAIN}",
                "X-Requested-With" to "XMLHttpRequest" // 表明是 AJAX 请求
            )
        )

        val request = Request.Builder()
            .url(updatePasswordUrl)
            .headers(headers)
            .post(formBody)
            .build()

        Timber.d("正在向 $updatePasswordUrl 发送 POST 请求修改密码")
        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) {
                val errorBody = it.body?.string()?.take(500) // 读取部分错误体用于调试
                Timber.e("修改密码请求失败: HTTP ${it.code}. Body: $errorBody")
                // 不抛出 SessionExpiredException，因为这不是会话问题，是操作本身的问题
                return@executeJwxtRequestWithRetry ServiceResult.Error("修改密码请求失败: HTTP ${it.code}")
            }

            val responseBody = it.body?.string()?.trim()
            Timber.i("修改密码响应原始文本: '$responseBody'")

            when (responseBody) {
                "1" -> {
                    Timber.i("密码修改成功。")

                     prefs.saveCredentials(prefs.getString(STUDENT_ID, ""), newPassword, prefs.getInt(ISP, 0))
                    // 目前不自动更新存储的密码
                    ServiceResult.Success(Unit)
                }
                "2" -> {
                    Timber.w("修改密码失败：服务器返回 '2' (可能是旧密码错误、新密码不符合要求等)。")
                    ServiceResult.Error("旧密码错误或新密码不符合要求") // 返回具体错误
                }
                else -> {
                    Timber.e("修改密码失败：收到未知的响应内容 '$responseBody'")
                    ServiceResult.Error("修改密码失败：未知的响应 '$responseBody'")
                }
            }
        }
    }

    /**
     * 获取 YKT (一卡通) 余额。
     * 注意：此功能依赖 DCP 接口，其认证流程可能与 JWXT 不同，需要确保 CAS 会话有效。
     * @return [ServiceResult] 包含一卡通余额的字符串，或错误信息
     */
    suspend fun getYktNum(): ServiceResult<String> {
        // 确保 CAS 登录（访问 DCP 通常需要 CAS 认证）
        // 注意：DCP 的会话管理可能独立于 JWXT，这里仅确保基础 CAS 登录
        val casLoginResult = ensureCasLogin() // 使用一个辅助函数确保 CASTGC 存在
        if (casLoginResult is ServiceResult.Error) {
            return ServiceResult.Error("获取一卡通余额失败：需要 CAS 登录 - ${casLoginResult.message}")
        }

        // 尝试访问 DCP 基础 URL 以设置可能的 Cookie (如 _WEU)
        // 这一步并非总是必需，且可能失败，作尽力而为尝试
        try {
            Timber.d("尝试访问 DCP URL 以确保 Cookie: ${ApiConstants.DCP_URL}")
            val request = Request.Builder().url(ApiConstants.DCP_URL).get().build()
            client.newCall(request).execute().close() // 发出请求并忽略结果
        } catch (e: Exception) {
            Timber.w(e, "访问 DCP 基础 URL 时出错（非关键）")
        }

        // 发起 DCP 调用获取余额
        return when (val result = makeDcpCall(ApiConstants.METHOD_GET_YKT_NUM)) {
            is ServiceResult.Success -> parseYktBalance(result.data)
            is ServiceResult.Error -> result // 直接返回 DCP 调用错误
        }
    }

    /**
     * 获取指定日期的课程表信息。
     * 注意：此接口似乎不直接依赖 JWXT 的 JSESSIONID，而是通过 weiXinID 查询，
     * 因此不使用 `executeJwxtRequestWithRetry` 包装。
     *
     * @param dateQuery 查询日期，格式为 "YYYY-MM-DD"。如果为 null 或空，则获取当天的课表。
     * @return [ServiceResult] 包含 [DayCourses] 或错误信息。
     */
    suspend fun getCourseSchedule(dateQuery: String? = null): ServiceResult<DayCourses> = safeServiceCall { // 使用基础的 safeServiceCall 包装
        Timber.d("开始获取课程表信息... 查询日期: ${dateQuery ?: "当天"}")

        // 这个接口似乎使用 weiXinID，不直接依赖 CAS/JWXT 会话，需要确认其认证方式
        val weiXinId = prefs.getWeiXinId()
        if (weiXinId.isBlank()) {
            Timber.w("缺少 weiXinID，无法获取课程表。")
            // 这里应该返回错误，因为接口需要这个参数
            // return@safeServiceCall ServiceResult.Error("配置错误：缺少 weiXinID") // safeServiceCall 不支持直接返回 ServiceResult
            throw IOException("配置错误：缺少 weiXinID") // 抛出异常让 safeServiceCall 捕获
        }

        val urlBuilder = ApiConstants.COURSE_SCHEDULE_URL.toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("内部错误：课程表 URL 配置无效")

        urlBuilder.addQueryParameter("weiXinID", weiXinId)
        if (!dateQuery.isNullOrBlank()) {
            urlBuilder.addQueryParameter("date", dateQuery)
        }
        val targetUrl = urlBuilder.build()

        val request = Request.Builder().url(targetUrl).get().build()
        Timber.d("正在向 $targetUrl 发送 GET 请求获取课程表")

        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) {
                throw IOException("获取课程表页面失败: HTTP ${it.code}")
            }
            val htmlBody = it.body?.string()
            if (htmlBody.isNullOrBlank()) {
                throw ParseException("课程表页面响应体为空")
            }
            parseCourseScheduleHtml(htmlBody) // 使用提取的解析函数，成功时返回 DayCourses
        }
    }

    // --- 私有辅助函数 ---

    /**
     * 核心的请求执行器，负责处理会话检查、自动重登录和重试逻辑。
     * @param maxAttempts 最大尝试次数（包括首次尝试）
     * @param requestBlock 实际执行网络请求并处理响应的挂起代码块。
     *                     该代码块应在成功时返回 [ServiceResult.Success<T>]。
     *                     在检测到登录页时，内部的 `handleJwxtResponse` 应抛出 [SessionExpiredException]。
     *                     其他失败情况（如网络错误、解析错误）也应通过抛出异常或返回 [ServiceResult.Error] 来处理。
     */
    private suspend fun <T> executeJwxtRequestWithRetry(
        maxAttempts: Int = maxLoginAttempts,
        requestBlock: suspend () -> ServiceResult<T> // requestBlock 现在必须返回 ServiceResult<T>
    ): ServiceResult<T> = withContext(Dispatchers.IO) {
        var currentAttempt = 1
        var lastErrorResult: ServiceResult<T>? = null // 保存最后一次的错误结果

        while (currentAttempt <= maxAttempts) {
            Timber.d("执行 JWXT 请求，尝试次数: $currentAttempt / $maxAttempts")

            // 1. 确保会话有效（如果无效则尝试登录）
            // ensureLogin 内部会处理登录失败并返回 Error
            val sessionResult = ensureLogin() // 确保 JWXT 会话
            if (sessionResult is ServiceResult.Error) {
                Timber.e("确保会话失败: ${sessionResult.message}")
                // 如果连确保会话都失败了，直接返回错误，不再尝试执行请求
                return@withContext sessionResult.castError() // Cast ServiceResult<Unit> to ServiceResult<T>
            }

            // 2. 执行实际的请求块
            try {
                val result = requestBlock() // 执行 requestBlock，期望返回 ServiceResult<T>
                // 如果请求块成功返回，则直接返回结果
                if (result is ServiceResult.Success) {
                    Timber.d("请求块成功返回。")
                    return@withContext result
                } else {
                    // 如果请求块内部逻辑判断失败并返回了 ServiceResult.Error
                    lastErrorResult = result
                    Timber.w("请求块返回错误 (非会话过期): ${(result as ServiceResult.Error).message}，尝试次数 $currentAttempt")
                    // 对于这类错误，通常不应重试（除非是可恢复的特定错误），直接跳出循环返回错误
                    // return@withContext result // 或者继续尝试？取决于错误类型，目前保守地继续尝试
                }
            } catch (e: SessionExpiredException) {
                // 3. 捕获到会话过期异常 (由 handleJwxtResponse 抛出)
                Timber.w("捕获到 SessionExpiredException (尝试 $currentAttempt/$maxAttempts): ${e.message}")
                lastErrorResult = ServiceResult.Error(e.message ?: "会话已过期", e) // 更新最后错误

                if (currentAttempt >= maxAttempts) {
                    Timber.e("已达到最大尝试次数 ($maxAttempts)，自动重新登录失败。")
                    break // 跳出循环，将返回 lastErrorResult
                }

                // 尝试强制重新登录 (使用互斥锁)
                val reLoginSuccess = reLoginMutex.withLock {
                    Timber.i("获取重登录锁，尝试强制重新登录...")
                    // 再次检查，可能在等待锁时其他线程已登录成功
                    if (checkJwxtSessionOnline()) {
                        Timber.i("会话在等待锁期间已恢复有效。")
                        true // 无需重新登录
                    } else {
                        // logout(clearStoredCredentials = false) // 清除旧 Cookie，performFullLogin 会处理
                        val reLoginResult = performFullLogin() // 执行完整登录
                        if (reLoginResult is ServiceResult.Success && checkJwxtSessionOnline()) {
                            Timber.i("强制重新登录成功并验证有效。")
                            true
                        } else {
                            val errorMsg = (reLoginResult as? ServiceResult.Error)?.message ?: "重新登录后会话验证失败"
                            Timber.e("强制重新登录失败: $errorMsg")
                            // 更新 lastErrorResult 为更具体的重登录失败信息
                            lastErrorResult = ServiceResult.Error("自动重新登录失败: $errorMsg", (reLoginResult as? ServiceResult.Error)?.exception).castError()
                            false
                        }
                    }
                }

                if (!reLoginSuccess) {
                    // 如果强制重登录失败，则直接跳出循环返回错误
                    break
                }
                // 如果重登录成功，则继续下一次循环尝试

            } catch (e: SocketTimeoutException) {
                Timber.w(e, "执行请求块时发生 SocketTimeoutException (尝试 $currentAttempt): ${e.message}")
                lastErrorResult = ServiceResult.Error("网络请求超时", e)
                // 超时通常应该重试

            } catch (e: IOException) {
                // 4. 捕获其他 IO 异常 (网络错误, ParseException 等)
                Timber.w(e, "执行请求块时发生 IO 错误 (尝试 $currentAttempt): ${e.message}")
                lastErrorResult = ServiceResult.Error(e.message ?: "网络或数据处理错误", e)
                // 网络错误通常会重试，解析错误不应重试
                if (e is ParseException || e.message?.contains("解析") == true) {
                    Timber.e("检测到解析错误，不再重试。")
                    break // 跳出循环
                }
            } catch (e: Exception) {
                // 5. 捕获其他未知异常
                Timber.e(e, "执行请求块时发生未知错误 (尝试 $currentAttempt): ${e.message}")
                lastErrorResult = ServiceResult.Error(e.message ?: "发生未知错误", e)
                // 未知错误通常不建议重试，直接跳出循环返回
                break
            }

            // 如果循环未返回或跳出，则增加尝试次数并可能进行延迟
            currentAttempt++
            if (currentAttempt <= maxAttempts) {
                delay(300L * (currentAttempt - 1)) // 增加延迟重试 (e.g., 0, 300, 600ms)
            }
        }

        // 如果循环结束仍未成功，返回最后记录的错误
        Timber.e("所有尝试 ($maxAttempts 次) 均失败或未成功。")
        return@withContext lastErrorResult ?: ServiceResult.Error("请求失败，已达最大尝试次数")
    }

    /**
     * 检查 JWXT 会话是否在线有效。
     * @return true 如果会话有效，false 如果无效或检查时出错。
     */
    private suspend fun checkJwxtSessionOnline(): Boolean = withContext(Dispatchers.IO) {
        if (!hasLogin(SessionType.JWXT)) {
            Timber.d("JWXT 在线检查: 失败 (本地缺少 JWXT cookie)")
            return@withContext false
        }

        Timber.d("JWXT 在线检查: 执行网络检查...")
        val checkUrl = ApiConstants.GET_STU_INFO_URL // 使用一个需要登录的页面进行检查

        try {
            val url = checkUrl.toHttpUrlOrNull() ?: return@withContext false // URL无效则检查失败
            val headers = buildJwxtHeaders(url.host, "$JWXT_ECJTU_DOMAIN/index.action") // 使用标准请求头
            val request = Request.Builder().url(url).headers(headers).get().build()

            // 使用一个临时的、不自动重定向的客户端来精确检查响应
            // 设置较短的超时时间，避免长时间阻塞
            val tempClient = client.newBuilder()
                .followRedirects(false)
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = tempClient.newCall(request).execute()

            response.use {
                // 如果直接返回登录页标识或者重定向到登录页，则认为会话无效
                val isRedirectToLogin = it.code == 302 && it.header("Location")?.contains("login", ignoreCase = true) == true
                if (isRedirectToLogin) {
                    Timber.w("JWXT 在线检查: 失败 (重定向到登录页)")
                    return@withContext false
                }
                // 如果返回非成功码（且非重定向），也认为可能无效
                if (!it.isSuccessful) {
                    Timber.w("JWXT 在线检查: 失败 (HTTP ${it.code})")
                    // 403 Forbidden 也可能表示会话问题
                    return@withContext false
                }
                // 检查响应体是否包含登录页标识（使用 peekBody 避免消耗）
                val bodyString = try { it.peekBody(1024 * 1024).string() } catch (e: Exception) { "" }
                if (bodyString.contains(JWXT_LOGIN_PAGE_IDENTIFIER)) {
                    Timber.w("JWXT 在线检查: 失败 (响应体包含登录页标识)")
                    return@withContext false
                }
                // 如果以上检查都通过，认为会话有效
                Timber.i("JWXT 在线检查: 成功，会话有效。")
                return@withContext true
            }
        } catch (e: SocketTimeoutException){
            Timber.w(e,"JWXT 在线检查: 超时") // 超时也认为会话可能无效或网络差
            return@withContext false
        }
        catch (e: IOException) {
            Timber.e(e, "JWXT 在线检查: 失败 (网络 IO 错误)")
            return@withContext false // IO 错误通常意味着无法验证，当作无效处理
        } catch (e: Exception) {
            Timber.e(e, "JWXT 在线检查: 失败 (未知错误)")
            return@withContext false
        }
    }


    /**
     * 检查 Cookie 是否存在。
     * @param type 需要检查的会话类型 (CAS 或 JWXT)。
     */
    private fun hasLogin(type: SessionType): Boolean {
        val urlsToCheck = listOfNotNull(
            ECJTU_LOGIN_URL.toHttpUrlOrNull(),
            JWXT_LOGIN_URL.toHttpUrlOrNull(),
            ApiConstants.JWXT_ECJTU_DOMAIN.toHttpUrlOrNull(), // 检查 JWXT 域本身
            ApiConstants.CAS_ECJTU_DOMAIN.toHttpUrlOrNull() // 检查 CAS 域本身
        )
        if (urlsToCheck.isEmpty()) {
            Timber.w("hasLogin 检查失败：无法解析必要的 URL。")
            return false
        }

        val allCookies = urlsToCheck.flatMap { url ->
            try {
                // 获取指定 URL 的所有 cookie，包括 HttpOnly 的
                cookieJar.loadForRequest(url).filter { cookie ->
                    // 过滤掉可能已过期的 cookie (如果 Cookie 类支持 expiresAt)
                    // cookie.expiresAt == null || cookie.expiresAt > System.currentTimeMillis()
                    // OkHttp 的 Cookie 类默认不过滤，PersistentCookieJar 可能会在加载时清理
                    true // 暂时不过滤过期时间
                }
            } catch (e: Exception) {
                Timber.w(e, "加载 $url 的 Cookie 失败")
                emptyList()
            }
        }.distinctBy { "${it.name}-${it.domain}" } // 去重，避免重复计算

        // Timber.v("hasLogin check for type $type, all loaded cookies: ${allCookies.joinToString { c -> "${c.name}=${c.value}@${c.domain}" }}")

        return when (type) {
            // CAS 只需要 CASTGC 在 CAS 域存在
            SessionType.CAS -> allCookies.any {
                it.name == COOKIE_CASTGC && it.value.isNotBlank() && it.matches(ECJTU_LOGIN_URL.toHttpUrl())
            }
            // JWXT 需要 CASTGC 在 CAS 域存在，且 JSESSIONID 在 JWXT 域存在
            SessionType.JWXT -> {
                val hasCas = allCookies.any { it.name == COOKIE_CASTGC && it.value.isNotBlank() && it.matches(ECJTU_LOGIN_URL.toHttpUrl()) }
                val hasJwxt = allCookies.any { it.name == COOKIE_JSESSIONID && it.value.isNotBlank() && it.matches(JWXT_LOGIN_URL.toHttpUrl()) }
                // Timber.v("JWXT check: hasCas=$hasCas, hasJwxt=$hasJwxt")
                hasCas && hasJwxt
            }
        }
    }

    /**
     * 执行完整的 CAS -> JWXT 登录流程。
     * 可选择提供用户名密码，否则从 Preferences 读取。
     * @param providedUsername 可选的用户名
     * @param providedPassword 可选的明文密码
     * @return [ServiceResult<Unit>]
     */
    private suspend fun performFullLogin(
        providedUsername: String? = null,
        providedPassword: String? = null
    ): ServiceResult<Unit> = safeServiceCall { // 使用 safeServiceCall 包装基础异常
        val username = providedUsername ?: prefs.getString(STUDENT_ID, "")
        val password = providedPassword ?: prefs.getString(PASSWORD, "")

        if (username.isBlank() || password.isBlank()) {
            throw IOException("登录失败：缺少用户名或密码")
        }

        Timber.i("正在为用户 $username 执行完整登录流程...")
        // 清理旧 Cookie 总是好的习惯，避免干扰
        cookieJar.clear()
        Timber.d("执行完整登录前清除 CookieJar。")


        // 1. 加密密码
        val encPassword = getEncryptedPassword(password).getOrThrow("密码加密失败")

        // 2. 获取 LT 值
        val headers = buildCasHeaders() // 构建 CAS 请求头
        val ltValue = getLoginLtValue(headers).getOrThrow("获取 LT 值失败")

        // 3. 提交登录凭据
        loginWithCredentials(username, encPassword, ltValue, headers).getOrThrow("登录凭据提交失败")
        // 短暂延迟，等待 Cookie 可能的异步写入？(理论上不需要)
        // delay(50)
        // 检查 CASTGC 是否设置成功
        if (!hasLogin(SessionType.CAS)) {
            // 登录调用成功但 Cookie 未设置，这很奇怪
            Timber.e("登录凭据提交后检查 CASTGC 失败！")
            throw IOException("登录失败：服务返回成功但未正确设置 CAS 凭据 (CASTGC)")
        }
        Timber.d("CASTGC Cookie 已成功设置。")

        // 4. 处理重定向以获取 JWXT 会话
        handleRedirection(headers).getOrThrow("登录重定向失败")
        // 检查 JWXT 会话是否设置成功
        // delay(50) // 短暂延迟
        if (!hasLogin(SessionType.JWXT)) {
            Timber.e("重定向处理后检查 JSESSIONID 失败！")
            throw IOException("登录失败：无法建立教务系统会话 (JSESSIONID)")
        }
        Timber.d("JSESSIONID Cookie 已成功设置。")


        Timber.i("用户 $username 的完整登录流程成功完成。")
        // 成功时此函数不返回数据 (返回 Unit)，由 safeServiceCall 包装为 ServiceResult.Success(Unit)
    }


    /** 获取加密后的密码 */
    private suspend fun getEncryptedPassword(plainPassword: String): ServiceResult<String> = safeServiceCall {
        val formBody = FormBody.Builder().add("pwd", plainPassword).build()
        val url = ApiConstants.PWD_ENC_URL.toHttpUrlOrNull() ?: throw IOException("密码加密URL无效")
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .headers(buildCasHeaders()) // 使用 CAS 头
            .build()

        Timber.d("正在加密密码...")
        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) throw IOException("密码加密失败: HTTP ${it.code}")
            val responseBody = it.body?.string()
            val passwordEnc = try {
                gson.fromJson(responseBody, JsonObject::class.java)?.getStringOrNull("passwordEnc")
            } catch (e: Exception) { null }

            if (passwordEnc.isNullOrBlank()) {
                Timber.e("在加密响应中找不到 'passwordEnc': ${responseBody?.take(500)}")
                throw IOException("在加密响应中找不到 'passwordEnc'")
            }
            Timber.d("密码加密成功。")
            passwordEnc
        }
    }

    /** 获取登录所需的 LT 值 */
    private suspend fun getLoginLtValue(headers: Headers): ServiceResult<String> = safeServiceCall {
        val url = ECJTU_LOGIN_URL.toHttpUrlOrNull() ?: throw IOException("CAS登录URL无效")
        Timber.d("正在从登录页面获取 LT 值: $url")
        val request = Request.Builder().url(url).headers(headers).get().build()
        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) throw IOException("获取登录页面失败: HTTP ${it.code}")
            val body = it.body?.string()
            val document = body?.let { Jsoup.parse(it) }
            // 尝试更健壮的选择器
            val ltValue = document?.selectFirst("input[name=lt][value]")?.attr("value")

            if (ltValue.isNullOrBlank()) {
                Timber.e("在登录页面 HTML 中找不到 'lt' 值。Body: ${body?.take(500)}")
                throw IOException("在登录页面 HTML 中找不到 'lt' 值")
            }
            Timber.d("LT 值已获取: $ltValue")
            ltValue
        }
    }

    /** 提交登录凭据 */
    private suspend fun loginWithCredentials(
        username: String,
        encryptedPass: String,
        ltValue: String,
        headers: Headers
    ): ServiceResult<Unit> = safeServiceCall {
        Timber.d("正在为 $username 提交登录凭据...")
        val loginRequestBody = FormBody.Builder()
            .add("username", username)
            .add("password", encryptedPass)
            .add("lt", ltValue)
            .add("execution", "e1s1") // 这个通常是需要的
            .add("_eventId", "submit") // 这个通常是需要的
            .add("submit", "登录") // 有时按钮的值也需要
            .build()

        val url = ECJTU_LOGIN_URL.toHttpUrlOrNull() ?: throw IOException("CAS登录URL无效")
        val request = Request.Builder()
            .url(url)
            .post(loginRequestBody)
            .headers(headers) // Host, User-Agent
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Referer", url.toString()) // Referer 应该是登录页本身
            .build()

        // 使用不自动重定向的客户端检查初始响应和 Cookie 设置
        val tempClient = client.newBuilder().followRedirects(false).build()
        val loginResponse = tempClient.newCall(request).execute()

        loginResponse.use { response ->
            Timber.d("登录 POST 响应代码: ${response.code}")
            response.headers("Set-Cookie").forEach { Timber.v("登录响应 Set-Cookie: $it") }

            // CASTGC 通常在 302 重定向的 Set-Cookie 头里
            val hasCastgcInHeader = response.headers("Set-Cookie").any { c -> c.startsWith("$COOKIE_CASTGC=TGT-") }
            // PersistentCookieJar 会处理 Set-Cookie，所以检查 Jar 内部
            val hasCastgcInJar = hasLogin(SessionType.CAS) // 重新检查 CookieJar

            // 如果响应不是重定向，并且 Header 和 Jar 里都没有 CASTGC，基本确定失败
            if (!response.isRedirect && !hasCastgcInHeader && !hasCastgcInJar) {
                val errorMsg = parseLoginError(response) ?: "登录失败：非重定向且未找到 CASTGC"
                Timber.e(errorMsg)
                throw IOException(errorMsg) // 抛出异常，包含解析到的错误信息
            }

            // 如果响应是重定向，或者响应不是重定向但 Jar 里有 CASTGC，则认为初步成功
            // （有时登录成功后也可能返回 200 OK 而不是 302）
            if (response.isRedirect || hasCastgcInJar) {
                Timber.d("登录凭据提交初步成功 (检测到重定向或 CASTGC)。")
            } else {
                // 非重定向，Header 中有但 Jar 中没有？可能 CookieJar 处理有延迟或问题
                Timber.w("登录响应非重定向，Header 中有 CASTGC 但 Jar 中未立即检测到，继续流程但需注意。")
                // 这种情况也继续，依赖后续的 hasLogin 检查
            }
            // 此处不需要返回值，成功则继续，失败则抛异常
        }
    }

    /** 解析登录失败时的错误信息 */
    private fun parseLoginError(response: Response): String? {
        // 尝试从响应体解析错误提示
        return try {
            // 确保响应体可读，即使在非 200 的情况下
            val bodyString = response.peekBody(1024 * 1024).string() // 使用 peek 避免消耗
            if (bodyString.isBlank()) {
                Timber.w("尝试解析登录错误，但响应体为空。HTTP ${response.code}")
                return null
            }
            // 尝试常见的 CAS 错误提示选择器
            val errorElement = Jsoup.parse(bodyString)
                .selectFirst("div#msg.errors, div.error, span.error, #errorMsg, .mistake_notice") // 更多可能的选择器
            errorElement?.text()?.trim()?.takeIf { it.isNotEmpty() } // 确保非空
        } catch (e: Exception) {
            Timber.w(e, "解析登录错误响应体时出错")
            null
        }
    }


    /** 处理 CAS 登录后的重定向流程，以建立 JWXT 会话 */
    private suspend fun handleRedirection(headers: Headers): ServiceResult<Unit> = safeServiceCall {
        Timber.d("正在处理登录后重定向...")

        // 步骤 1: 访问 JWXT 登录 URL，触发 CAS 认证和重定向
        Timber.d("正在访问 JWXT 登录 URL: $JWXT_LOGIN_URL")
        val jwxtLoginUrl = JWXT_LOGIN_URL.toHttpUrlOrNull() ?: throw IOException("JWXT 登录 URL无效")
        val jwxtHost = jwxtLoginUrl.host
        val jwxtLoginRequest = Request.Builder()
            .url(jwxtLoginUrl)
            // 使用 JWXT 的头，CookieJar 会自动带上 CASTGC
            .headers(buildJwxtHeaders(jwxtHost, referer = ECJTU_LOGIN_URL)) // Referer 可以是 CAS 登录页
            .get()
            .build()

        // 使用 *跟踪重定向* 的主客户端执行
        // OkHttp 默认会处理 CookieJar 中与重定向相关的 Cookie 传递
        client.newCall(jwxtLoginRequest).execute().use { response ->
            Timber.d("JWXT 登录 URL 访问最终响应代码: ${response.code}, URL: ${response.request.url}")
            response.headers("Set-Cookie").forEach { Timber.v("JWXT 重定向 Set-Cookie: $it") }

            // 检查最终响应的 URL 是否是 JWXT 的某个内部页面，而不是又跳回登录页
            if (!response.isSuccessful || response.request.url.toString().contains("login", ignoreCase = true)) {
                Timber.w("JWXT 登录重定向步骤最终响应不成功或仍在登录页 (HTTP ${response.code}, URL: ${response.request.url})")
                // 检查 JSESSIONID 是否已设置
                if (!hasLogin(SessionType.JWXT)) {
                    throw IOException("通过 CAS 建立 JWXT 会话失败: 最终响应异常且未设置会话 Cookie")
                } else {
                    Timber.w("虽然最终响应异常，但 JSESSIONID 已设置，谨慎继续。")
                }
            } else {
                Timber.d("JWXT 登录重定向步骤初步成功 (最终 HTTP ${response.code})。")
            }
        }
        delay(100) // 短暂延迟，确保 Cookie 处理完成

        Timber.d("正在访问显式 CAS 服务 URL 指向 JWXT: ${ApiConstants.ECJTU2JWXT_URL}")
        val finalRedirectUrl = ApiConstants.ECJTU2JWXT_URL.toHttpUrlOrNull() ?: throw IOException("CAS 服务 URL无效")
        val finalRedirectRequest = Request.Builder()
            .url(finalRedirectUrl)
            .headers(buildCasHeaders()) // 访问 CAS URL 使用 CAS 的头
            .get()
            .build()

        client.newCall(finalRedirectRequest).execute().use { response ->
            Timber.d("显式 CAS->JWXT 重定向最终响应代码: ${response.code}, URL: ${response.request.url}")
            if (!response.isSuccessful || response.request.url.toString().contains("login", ignoreCase = true)) {
                Timber.w("最终 CAS->JWXT 重定向步骤最终响应不成功或仍在登录页 (HTTP ${response.code}, URL: ${response.request.url})")
                if (!hasLogin(SessionType.JWXT)) {
                    throw IOException("最终 CAS->JWXT 重定向失败: HTTP ${response.code} 且未设置会话 Cookie")
                } else {
                    Timber.w("虽然最终响应异常，但 JSESSIONID 已设置。")
                }
            } else {
                Timber.d("最终 CAS->JWXT 重定向步骤成功完成 (HTTP ${response.code})。")
            }
        }

        Timber.d("重定向处理完成。")
        // 如果没有抛出异常则表示成功
    }

    /** 如果需要，处理重定向 (只有 CAS 会话，没有 JWXT 会话时) */
    private suspend fun handleRedirectionIfNeeded(): ServiceResult<Unit> {
        return if (hasLogin(SessionType.CAS) && !hasLogin(SessionType.JWXT)) {
            Timber.d("检测到需要重定向 (有 CAS 无 JWXT)。")
            handleRedirection(buildCasHeaders()) // 使用 CAS 头执行重定向
        } else {
            Timber.d("无需处理重定向 (状态: CAS=${hasLogin(SessionType.CAS)}, JWXT=${hasLogin(SessionType.JWXT)})。")
            // 如果已经有 JWXT 会话，那自然是成功的
            if (hasLogin(SessionType.JWXT)) ServiceResult.Success(Unit)
            // 如果连 CAS 都没有，那重定向也无意义，让 ensureLogin/performFullLogin 处理
            else ServiceResult.Error("无法重定向：缺少 CAS 会话")
        }
    }

    /**
     * 确保基础 CAS 登录 (CASTGC) 有效。
     * 如果无效，尝试使用存储的凭据登录 CAS 部分。
     * @return [ServiceResult<Unit>]
     */
    private suspend fun ensureCasLogin(): ServiceResult<Unit> = safeServiceCall {
        if (hasLogin(SessionType.CAS)) {
            Timber.d("CAS 会话已存在。")
            return@safeServiceCall // 直接返回成功 (Unit)
        }

        Timber.i("CAS 会话不存在，尝试仅登录 CAS...")
        val username = prefs.getString(STUDENT_ID, "")
        val password = prefs.getString(PASSWORD, "")
        if (username.isBlank() || password.isBlank()) {
            throw IOException("无法登录 CAS：缺少存储的凭据")
        }

        // 执行 CAS 登录部分
        val encPassword = getEncryptedPassword(password).getOrThrow("密码加密失败")
        val headers = buildCasHeaders()
        val ltValue = getLoginLtValue(headers).getOrThrow("获取 LT 值失败")
        // 只提交凭据，不处理后续重定向到 JWXT
        loginWithCredentials(username, encPassword, ltValue, headers).getOrThrow("CAS 登录凭据提交失败")

        // 再次检查 CASTGC
        if (!hasLogin(SessionType.CAS)) {
            throw IOException("CAS 登录后仍未找到 CASTGC")
        }
        Timber.i("仅 CAS 登录成功。")
        // 成功时由 safeServiceCall 包装返回 Success(Unit)
    }

    /** 向 DCP 端点发出 POST 请求 */
    private suspend fun makeDcpCall(method: String, params: Map<String, Any>? = null): ServiceResult<String> = safeServiceCall {
        Timber.d("正在进行 DCP 调用: method=$method, params=$params")
        val requestPayload = mapOf(
            "map" to mapOf("method" to method, "params" to params).filterValues { it != null },
            "javaClass" to "java.util.HashMap"
        )
        val requestBodyString = gson.toJson(requestPayload)
        Timber.v("DCP 请求负载: $requestBodyString")
        val requestBody = requestBodyString.toRequestBody("application/json; charset=utf-8".toMediaType())

        val dcpUrl = ApiConstants.DCP_SSO_URL.toHttpUrlOrNull() ?: throw IOException("DCP URL 无效")
        val headers = Headers.Builder()
            .add("render", "json")
            .add("clientType", "json")
            .add("User-Agent", ApiConstants.USER_AGENT)
            .add("Host", dcpUrl.host)
            .add("Referer", ApiConstants.DCP_URL) // DCP Referer 可能重要
            // 可能还需要 X-Requested-With?
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val request = Request.Builder()
            .url(dcpUrl)
            .post(requestBody)
            .headers(headers)
            .build()

        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) throw IOException("DCP 调用 '$method' 失败: HTTP ${it.code}")
            val responseBody = it.body?.string()
            if (responseBody.isNullOrBlank()) throw IOException("DCP 调用 '$method' 的响应体为空")
            Timber.v("DCP 响应体 ($method): ${responseBody.take(500)}")
            responseBody // 返回响应体字符串
        }
    }

    /** 解析一卡通余额响应 */
    private fun parseYktBalance(jsonData: String): ServiceResult<String> {
        return try {
            val json = gson.fromJson(jsonData, JsonObject::class.java)
            // DCP 返回结构可能嵌套较深
            val balance = json.getAsJsonObject("map") // 或者其他可能的顶层 key
                ?.getStringOrNull("balance") // 或者实际包含余额的 key
            if (balance != null && balance.matches(Regex("""\d+(\.\d{1,2})?"""))) {
                Timber.d("一卡通余额已获取: $balance")
                ServiceResult.Success(balance)
            } else {
                Timber.e("从 JSON map 解析一卡通余额失败或格式无效: ${jsonData.take(500)}")
                ServiceResult.Error("解析余额数据失败: 格式不符或未找到")
            }
        } catch (e: Exception) {
            Timber.e(e, "解析一卡通余额 JSON 失败: ${jsonData.take(500)}")
            ServiceResult.Error("解析余额数据失败", e)
        }
    }

    /** 解析课程表 HTML */
    private fun parseCourseScheduleHtml(htmlBody: String): DayCourses {
        Timber.d("开始解析课程表 HTML...")
        val document = Jsoup.parse(htmlBody)

        // 提取日期
        val extractedDate = document.selectFirst("div.center > p")?.text()?.trim() // 更精确的选择器
        val scheduleDate = if (extractedDate.isNullOrBlank()) "日期未知" else extractedDate
        Timber.d("解析得到的日期: $scheduleDate")

        // 定位课程列表区域
        val courseListElement = document.selectFirst("div.calendar ul.rl_info")

        // 如果列表区域不存在，或包含无课图片，则返回空列表
        if (courseListElement == null || courseListElement.selectFirst("li > p > img[alt=当天无课]") != null) { // 更精确的图片选择器
            val reason = if (courseListElement == null) "未找到列表容器" else "检测到无课占位符"
            Timber.i("当天无课 ($reason)。返回空课表。")
            return DayCourses(scheduleDate, emptyList())
        }

        val courses = mutableListOf<CourseInfo>()
        val listItems = courseListElement.select("li") // 获取所有课程条目

        if (listItems.isEmpty() && courseListElement.text().contains("当天无课")) { // 备用检查：列表为空且文本提示无课
            Timber.i("课程列表为空且文本提示无课。返回空课表。")
            return DayCourses(scheduleDate, emptyList())
        } else if (listItems.isEmpty()){
            Timber.i("课程列表容器存在，但无课程条目 (li)。返回空课表。")
            return DayCourses(scheduleDate, emptyList())
        }


        Timber.d("找到 ${listItems.size} 个课程条目，开始解析...")
        for (item in listItems) {
            try {
                val pElement = item.selectFirst("p") ?: continue
                pElement.select("br").append("\\n") // 将 <br> 替换为换行符
                val lines = pElement.text().split("\\n").map { it.trim() }.filter { it.isNotEmpty() }

                // 根据行内容提取信息 (需要根据实际 HTML 结构调整)
                // 示例解析逻辑 (假设每行对应一个信息)
                val courseName = lines.getOrNull(0) ?: "N/A"
                val timeInfoLine = lines.getOrNull(1) ?: "" // 时间：xxx 第x-x节
                val courseWeek = timeInfoLine.substringBefore(" 第", "").removePrefix("时间：").trim()
                val courseTime = timeInfoLine.substringAfter(" 第", "").trim() // "x-x节"
                val courseLocation = lines.getOrNull(2)?.removePrefix("地点：")?.trim() ?: "N/A"
                val courseTeacher = lines.getOrNull(3)?.removePrefix("教师：")?.trim() ?: "N/A"


                if (courseName != "N/A") {
                    courses.add(
                        CourseInfo(
                            courseName = courseName,
                            courseTime = "节次：$courseTime", // 格式化输出
                            courseWeek = "上课周：$courseWeek", // 格式化输出
                            courseLocation = "地点：$courseLocation", // 格式化输出
                            courseTeacher = "教师：$courseTeacher" // 格式化输出
                        )
                    )
                } else {
                    Timber.w("跳过一个条目，未能解析出有效的课程名称: ${pElement.html()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "解析单个课程条目时出错: ${item.html()}")
                continue // 继续解析下一个条目
            }
        }

        Timber.i("课程表解析成功，日期: $scheduleDate, 课程数: ${courses.size}")
        return DayCourses(scheduleDate, courses)
    }


    /**
     * 通用的 JWXT 响应处理器。
     * 检查响应是否成功，是否为登录页，并返回 HTML 内容或抛出异常。
     * @param response OkHttp 响应对象
     * @param operationDesc 操作描述，用于日志记录
     * @return 成功时返回 HTML 字符串
     * @throws IOException 如果网络请求失败或响应体为空
     * @throws SessionExpiredException 如果检测到登录页面
     */
    @Throws(IOException::class, SessionExpiredException::class)
    private fun handleJwxtResponse(response: Response, operationDesc: String): String {
        // response.use 块确保响应体最终会被关闭
        response.use {
            // 检查是否重定向到登录页
            // 注意：这里使用的是原始 response，如果 OkHttp 自动处理了重定向，这里的 code 可能是最终页面的 code
            // 但如果用的是不允许重定向的 client，这里可以捕捉到 302
            // 假设使用的是允许重定向的主 client
            val finalUrl = it.request.url.toString()
            if (finalUrl.contains("login", ignoreCase = true) && !finalUrl.startsWith(ApiConstants.JWXT_ECJTU_DOMAIN)) {
                // 如果最终 URL 包含 login 且不是 JWXT 域内的（说明跳出去了），则认为是会话过期
                Timber.w("$operationDesc: 失败 (最终重定向到登录页 $finalUrl)")
                throw SessionExpiredException("$operationDesc: Session expired (redirected to login page)")
            }

            if (!it.isSuccessful) {
                val errorBody = try { it.peekBody(512).string() } catch (e:Exception){ "(无法读取响应体)" }
                Timber.e("$operationDesc 失败: HTTP ${it.code}. URL: $finalUrl. Body: $errorBody")
                // 特殊处理 403 Forbidden，也可能表示会话问题或权限不足
                if (it.code == 403) {
                    throw SessionExpiredException("$operationDesc: Access Denied (HTTP 403) - 可能需要重新登录")
                }
                throw IOException("$operationDesc 失败: HTTP ${it.code}")
            }

            // 读取响应体（只能读一次）
            val htmlBody = it.body?.string()

            // 检查响应体是否包含登录页的明确标识
            // 注意：成功页面也可能包含 "login" 字符串，需要更精确的标识符
            if (htmlBody?.contains(JWXT_LOGIN_PAGE_IDENTIFIER) == true) {
                Timber.w("$operationDesc: 失败 (响应体包含登录页标识)")
                throw SessionExpiredException("$operationDesc: Session expired (login page content detected)")
            }

            if (htmlBody.isNullOrBlank()) {
                Timber.e("$operationDesc 成功 (HTTP ${it.code})，但响应体为空。 URL: $finalUrl")
                // 根据业务逻辑，空响应体是否算错误？
                // 如果期望必须有内容，则抛出异常
                throw ParseException("$operationDesc 成功，但响应内容为空")
            }

            Timber.i("成功获取 $operationDesc HTML。")
            return htmlBody
        }
    }


    /**
     * 构建通用的 CAS 请求头。
     */
    private fun buildCasHeaders(): Headers {
        return Headers.Builder()
            .add("User-Agent", ApiConstants.USER_AGENT)
            .add("Host", ApiConstants.CAS_ECJTU_DOMAIN)
            .add("Accept", "*/*")
            .add("Accept-Language", "zh-CN,zh;q=0.9")
            .add("Connection", "keep-alive")
            .add("Sec-Fetch-Dest", "document")
            .add("Sec-Fetch-Mode", "navigate")
            .add("Sec-Fetch-Site", "same-origin")
            .add("Upgrade-Insecure-Requests", "1")
            .build()
    }

    /**
     * 构建通用的 JWXT 请求头。
     * @param host 目标主机
     * @param referer Referer 头，通常是上一个页面的 URL
     * @param extraHeaders 额外的请求头 Map
     */
    private fun buildJwxtHeaders(host: String, referer: String? = null, extraHeaders: Map<String, String>? = null): Headers {
        val builder = Headers.Builder()
            .add("Host", host)
            .add("User-Agent", ApiConstants.USER_AGENT)
            // Accept 头根据请求类型调整，获取 HTML 通常是 text/html
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
            .add("Accept-Language", "zh-CN,zh;q=0.9")
            .add("Connection", "keep-alive")
            .add("Sec-Fetch-Dest", "document")
            .add("Sec-Fetch-Mode", "navigate")
            .add("Sec-Fetch-Site", "same-origin")
            .add("Upgrade-Insecure-Requests", "1")

        referer?.let { builder.add("Referer", it) }
        extraHeaders?.forEach { (key, value) -> builder.add(key, value) }

        return builder.build()
    }


    /**
     * 简化的 API 调用包装器，仅用于捕获异常并转换为 ServiceResult。
     * 不再处理重试逻辑，重试由 executeJwxtRequestWithRetry 或调用方负责。
     * 主要用于不涉及复杂会话管理的调用，如 getCourseSchedule, ensureCasLogin, makeDcpCall 等。
     * @param serviceCall 实际执行的挂起函数，成功时返回 T，失败时抛出异常。
     * @return [ServiceResult<T>]
     */
    private suspend fun <T> safeServiceCall(
        serviceCall: suspend () -> T
    ): ServiceResult<T> = withContext(Dispatchers.IO) {
        try {
            ServiceResult.Success(serviceCall())
        } catch (e: SessionExpiredException) {
            // 这个异常通常由 executeJwxtRequestWithRetry 处理，但如果 safeServiceCall 直接包装了可能触发的逻辑，也需要处理
            Timber.w("safeServiceCall 捕获到 SessionExpiredException: ${e.message}")
            ServiceResult.Error(e.message ?: "会话已过期", e)
        } catch (e: SocketTimeoutException) {
            Timber.w(e, "API 调用超时: ${e.message}")
            ServiceResult.Error("网络请求超时，请检查网络连接", e)
        }
        catch (e: ParseException){
            Timber.w(e, "数据解析错误: ${e.message}")
            ServiceResult.Error(e.message ?: "数据解析失败", e)
        }
        catch (e: IOException) {
            // 包含网络错误和其他 IO 问题
            Timber.w(e, "API 调用失败 (IO): ${e.message}")
            ServiceResult.Error(e.message ?: "网络或IO错误", e)
        } catch (e: Exception) {
            Timber.e(e, "API 调用期间发生意外错误: ${e.message}")
            ServiceResult.Error(e.message ?: "发生未知错误", e)
        }
    }

    // --- 工具扩展/类 ---
    /** 用于更安全的 Gson JSON 解析的辅助扩展函数 */
    private fun JsonObject.getStringOrNull(key: String): String? {
        return try {
            this.get(key)?.takeIf { !it.isJsonNull }?.asString
        } catch (e: Exception) {
            Timber.w("从 JsonObject 获取字符串 '$key' 失败: ${e.message}")
            null
        }
    }

    /** 辅助扩展函数，用于将 ServiceResult<Any> 转换为 ServiceResult<T>，仅用于错误情况 */
    private fun <T> ServiceResult<*>.castError(): ServiceResult<T> {
        // 确保当前是 Error 类型
        if (this is ServiceResult.Error) {
            return ServiceResult.Error(this.message, this.exception)
        } else {
            // 或者记录错误并返回一个通用的 Error？
            Timber.e("尝试将非 Error 的 ServiceResult 转换为 Error<T>")
            return ServiceResult.Error("内部错误：无法转换结果类型")
        }
    }


    /** 辅助函数，用于安全地执行 getOrThrow，主要在内部流程中使用 */
    @Throws(IOException::class) // 声明可能抛出的异常类型
    private fun <T> ServiceResult<T>.getOrThrow(errorMessagePrefix: String): T {
        return when (this) {
            is ServiceResult.Success -> this.data
            is ServiceResult.Error -> {
                // 将原始异常包装起来，保留堆栈信息
                val cause = this.exception
                val message = "$errorMessagePrefix: ${this.message}" + (if(cause != null) " (Cause: ${cause.message})" else "")
                throw IOException(message, cause) // 抛出 IOException 或更具体的异常
            }
        }
    }
}