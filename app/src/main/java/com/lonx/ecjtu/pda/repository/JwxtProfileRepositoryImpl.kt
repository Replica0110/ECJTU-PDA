package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.common.ProfileType
import com.lonx.ecjtu.pda.common.toName
import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.common.log
import com.lonx.ecjtu.pda.data.common.mapCatching
import com.lonx.ecjtu.pda.data.common.onError
import com.lonx.ecjtu.pda.data.common.onSuccess
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.repository.ProfileRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException

class JwxtProfileRepositoryImpl(
    apiClient: JwxtApiClient,
    authRepository: AuthRepository
) : BaseJwxtRepository(apiClient, authRepository), ProfileRepository
{
    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)
    override suspend fun getStudentProfile(): PDAResult<Map<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        Timber.d("JwxtProfileRepository: 开始获取学生信息...")

        fetchHtmlWithRelogin {
            apiClient.getProfileHtml()
                .onError { msg, _ -> Timber.e("StuInfoService: 获取学生信息 HTML 失败: $msg") }
        }
            .onSuccess { Timber.d("JwxtProfileRepository: HTML 获取成功，开始解析...") }
            .mapCatching { html ->
                if (html.isBlank()) throw ParseException("学生信息页面内容为空")
                val profileData = parseCategorizedProfile(html)
                if (profileData.isEmpty()) Timber.w("JwxtProfileRepository: 未能从页面解析出任何学生信息。")
                else Timber.i("JwxtProfileRepository: 成功解析 ${profileData.size} 个信息分类。")
                profileData
            }
            .onError { msg, e ->
                Timber.e(e, "JwxtProfileRepository: 获取或解析学生信息失败: $msg")
            }
            .log("JwxtProfileRepository")
    }


    /**
     * Parses the HTML document and categorizes student information.
     * Moved from StuProfileService and made private.
     */
    private fun parseCategorizedProfile(html: String): Map<String, Map<String, String>> {
        val document = Jsoup.parse(html)
        val categorizedDataMap = mutableMapOf<String, Map<String, String>>()

        // 基本信息
        parseStuInfoInternal(document, "div#basic table.table_border", "k", "v")
            .takeIf { it.isNotEmpty() }
            ?.also { categorizedDataMap[ProfileType.BASE.toName()] = it }

        // 联系信息
        parseStuInfoInternal(document, "table#view_basic", "k_l", "v_l")
            .takeIf { it.isNotEmpty() }
            ?.also { categorizedDataMap[ProfileType.CONTACT.toName()] = it }

        return categorizedDataMap
    }

    /**
     * Internal helper to parse key-value pairs from a specific table structure.
     * Moved from StuProfileService and made private. Renamed to avoid conflict if needed.
     */
    private fun parseStuInfoInternal(
        document: Document,
        tableSelector: String,
        keyCssClass: String,
        valueCssClass: String
    ): Map<String, String> {
        val infoMap = mutableMapOf<String, String>()
        val table = document.selectFirst(tableSelector)
            ?: run {
                Timber.w("JwxtProfileRepository: 未找到信息表格: '$tableSelector'")
                return emptyMap()
            }

        val rows = table.select("tr")
        if (rows.isEmpty()) {
            Timber.w("JwxtProfileRepository: 表格 '$tableSelector' 中没有数据行。")
            return emptyMap()
        }

        for (row in rows) {
            val cells = row.select("td")
            var i = 0
            while (i < cells.size) {
                val currentCell = cells[i]

                if (currentCell.hasClass(keyCssClass)) {
                    if (i + 1 < cells.size) {
                        val nextCell = cells[i + 1]
                        val key = currentCell.text().trim()
                        var value = nextCell.text().trim()

                        if (key.isNotEmpty()) {
                            if (key == "电子邮件：") {
                                val emailSpanText = nextCell.selectFirst("span.mail")?.text()?.trim()
                                if (!emailSpanText.isNullOrBlank()) {
                                    value = emailSpanText
                                }
                            }
                            infoMap[key] = value
                            i += 2
                        } else {
                            Timber.v("JwxtProfileRepository: Skipped blank key in '$tableSelector'.")
                            i += 2
                        }
                    } else {
                        Timber.w("JwxtProfileRepository: Key cell '${keyCssClass}' found at end of row in '$tableSelector'.")
                        i += 1
                    }
                } else {
                    i += 1
                }
            }
        }
        return infoMap
    }
}