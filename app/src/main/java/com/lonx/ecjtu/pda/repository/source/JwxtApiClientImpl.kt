package com.lonx.ecjtu.pda.repository.source

import com.google.gson.Gson
import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.local.prefs.PreferencesManager
import com.lonx.ecjtu.pda.data.remote.ApiConstants
import com.lonx.ecjtu.pda.data.remote.ApiConstants.JWXT_ECJTU_DOMAIN
import com.lonx.ecjtu.pda.data.remote.ApiConstants.JWXT_LOGIN_PAGE_IDENTIFIER
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

class JwxtApiClientImpl(
    private val client: OkHttpClient,
    private val prefs: PreferencesManager,
    private val gson: Gson
) : JwxtApiClient {

    private val maxRetries = 3

    override suspend fun getStudentScoresHtml(): ServiceResult<String> = safeApiCall {
        val url = ApiConstants.GET_SCORE_URL.toHttpUrlOrNull() ?: throw IOException("Invalid Score URL")
        fetchHtmlInternal(url = url, referer = "$JWXT_ECJTU_DOMAIN/") // Default referer
    }

    override suspend fun getSecondCreditHtml(): ServiceResult<String> = safeApiCall {
        val url = ApiConstants.GET_SECOND_CREDIT.toHttpUrlOrNull() ?: throw IOException("Invalid Second Credit URL")
        fetchHtmlInternal(url = url, referer = "$JWXT_ECJTU_DOMAIN/")
    }

    override suspend fun getProfileHtml(): ServiceResult<String> = safeApiCall {
        val url = ApiConstants.GET_STU_INFO_URL.toHttpUrlOrNull() ?: throw IOException("Invalid Profile URL")
        fetchHtmlInternal(url = url, referer = "$JWXT_ECJTU_DOMAIN/index.action")
    }

    override suspend fun getCourseScheduleHtml(dateQuery: String?): ServiceResult<String> = safeApiCall {
        // This one seemed to have a different structure, implement directly
        Timber.d("获取课程表 HTML，查询日期: ${dateQuery ?: "未指定"}")
        val weiXinId = prefs.getWeiXinId() // Assuming this helper exists in PrefsManager
        if (weiXinId.isBlank()) {
            throw IOException("配置错误：缺少 weiXinID") // Throw exception for safeApiCall to catch
        }

        val urlBuilder = ApiConstants.COURSE_SCHEDULE_URL.toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("内部错误：课程表 URL 配置无效")

        urlBuilder.addQueryParameter("weiXinID", weiXinId)
        if (!dateQuery.isNullOrBlank()) {
            urlBuilder.addQueryParameter("date", dateQuery)
        }
        val finalUrl = urlBuilder.build()

        val request = Request.Builder()
            .url(finalUrl)
            // Add necessary headers if different from default fetchHtmlInternal headers
            .addHeader("User-Agent", ApiConstants.USER_AGENT)
            .get()
            .build()

        Timber.d("正在向 $finalUrl 发送 GET 请求获取课程表")
        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) throw IOException("获取课程表失败：HTTP ${it.code}")
            val html = it.body?.string()
            if (html.isNullOrBlank()) throw IOException("获取课程表响应体为空")

            // Check for login page *after* successful fetch
            if (isLoginRedirect(it, html)) {
                throw SessionExpiredException("获取课程表失败：会话已过期")
            }
            html // Return HTML on success
        }
    }


    override suspend fun getScheduleHtml(term: String?): ServiceResult<String> = safeApiCall {
        val url = ApiConstants.GET_SCHEDULE.toHttpUrlOrNull() ?: throw IOException("Invalid Schedule URL")
        fetchHtmlInternal(
            url = url,
            params = if (term.isNullOrBlank()) null else mapOf("term" to term),
            referer = "$JWXT_ECJTU_DOMAIN/index.action"
        )
    }

    override suspend fun getElectiveCourseHtml(term: String?): ServiceResult<String> = safeApiCall {
        val url = ApiConstants.GET_ELECTIVE_COURSE_URL.toHttpUrlOrNull() ?: throw IOException("Invalid Elective Course URL")
        fetchHtmlInternal(
            url = url,
            params = if (term.isNullOrBlank()) null else mapOf("term" to term),
            referer = "$JWXT_ECJTU_DOMAIN/index.action"
        )
    }

    override suspend fun getExperimentsHtml(term: String?): ServiceResult<String> = safeApiCall {
        val url = ApiConstants.GET_EXPERIMENT.toHttpUrlOrNull() ?: throw IOException("Invalid Experiment URL")
        fetchHtmlInternal(
            url = url,
            params = if (term.isNullOrBlank()) null else mapOf("term" to term),
            referer = "$JWXT_ECJTU_DOMAIN/index.action"
        )
    }

    override suspend fun getYktBalance(): ServiceResult<String> = safeApiCall {


        makeDcpCallInternal(ApiConstants.METHOD_GET_YKT_NUM)
    }


    /** Internal helper for fetching standard HTML pages. Does NOT handle re-login. */
    private fun fetchHtmlInternal(
        url: HttpUrl,
        referer: String?,
        params: Map<String, String>? = null,
        buildRequest: ((HttpUrl, Headers) -> Request)? = null
    ): String {
        val defaultHeaders = Headers.Builder()
            .add("Host", url.host)
            .add("User-Agent", ApiConstants.USER_AGENT)
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .apply { referer?.let { add("Referer", it) } }
            .build()

        val finalUrl = url.newBuilder().apply {
            params?.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        val request = buildRequest?.invoke(finalUrl, defaultHeaders)
            ?: Request.Builder().url(finalUrl).headers(defaultHeaders).get().build()

        Timber.d("Fetching HTML from: ${request.url}")
        val response = client.newCall(request).execute()

        response.use {
            val responseBody = it.body?.string()

            if (isLoginRedirect(it, responseBody)) {
                Timber.e("会话已过期，请重新登录")
                throw SessionExpiredException("会话已过期，请重新登录")
            }

            if (!it.isSuccessful) {
                Timber.e("请求失败: HTTP ${it.code}")
                throw IOException("请求失败: HTTP ${it.code}")
            }

            if (responseBody.isNullOrBlank()) {
                Timber.e("响应内容为空")
                throw IOException("响应内容为空")
            }
            return responseBody
        }
    }

    /** Internal helper for making DCP calls. */
    private fun makeDcpCallInternal(
        method: String,
        params: Map<String, Any>? = null
    ): String {
        Timber.d("正在进行 DCP 调用: method=$method, params=$params")
        val requestPayload = mapOf(
            "map" to mapOf("method" to method, "params" to params).filterValues { it != null },
            "javaClass" to "java.util.HashMap"
        )
        val requestBodyString = gson.toJson(requestPayload)
        val requestBody = requestBodyString.toRequestBody("application/json; charset=utf-8".toMediaType())

        val dcpUrlHttp = ApiConstants.DCP_SSO_URL.toHttpUrlOrNull() ?: throw IOException("Invalid DCP SSO URL")

        val request = Request.Builder()
            .url(dcpUrlHttp)
            .post(requestBody)
            .addHeader("render", "json")
            .addHeader("clientType", "json")
            .addHeader("User-Agent", ApiConstants.USER_AGENT)
            .addHeader("Host", dcpUrlHttp.host)
            .addHeader("Referer", ApiConstants.DCP_URL)
            .build()

        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) {
                throw IOException("DCP 调用 '$method' 失败: HTTP ${it.code}")
            }
            val responseBody = it.body?.string()
            if (responseBody.isNullOrBlank()) {
                throw IOException("DCP 调用 '$method' 的响应体为空")
            }
            Timber.v("DCP 响应体 ($method): ${responseBody.take(500)}")
            return responseBody
        }
    }


    /** Generic wrapper for API calls in this client, handling retries for network issues. */
    private suspend fun <T> safeApiCall(apiCall: suspend () -> T): ServiceResult<T> = withContext(
        Dispatchers.IO) {
        var currentRetries = 0
        while (currentRetries < maxRetries) {
            try {
                val result = apiCall()
                return@withContext ServiceResult.Success(result)
            } catch (e: SessionExpiredException) {
                Timber.w("API 调用失败：会话已过期 (${e.message})")
                return@withContext ServiceResult.Error(e.message ?: "会话已过期", e)
            } catch (e: IOException) {
                currentRetries++
                Timber.w(e, "API 调用失败 (尝试 ${currentRetries}/${maxRetries}): ${e.message}")
                if (currentRetries >= maxRetries) {
                    return@withContext ServiceResult.Error(e.message ?: "网络请求失败", e)
                }
                delay(500L * currentRetries)
            } catch (e: Exception) {
                Timber.e(e, "API 调用期间发生意外错误: ${e.message}")
                return@withContext ServiceResult.Error("发生意外错误: ${e.message}", e)
            }
        }
        ServiceResult.Error("达到最大重试次数后请求失败")
    }

    /** Checks if the response indicates a redirect to a login page or contains login page content. */
    private fun isLoginRedirect(response: Response, body: String?): Boolean {
        if (response.isRedirect) {
            val location = response.header("Location")
            if (location?.contains("login", ignoreCase = true) == true) {
                Timber.w("Detected redirect to login page: $location")
                return true
            }
        }
        if (body?.contains(JWXT_LOGIN_PAGE_IDENTIFIER, ignoreCase = true) == true) {
            Timber.w("Detected login page identifier in response body.")
            return true
        }
        return false
    }

    class SessionExpiredException(message: String) : IOException(message)
}