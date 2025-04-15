package com.lonx.ecjtu.pda.service // Or your appropriate package

import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.getOrNull
import com.lonx.ecjtu.pda.data.onError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber

class StuProfileService(
    private val service: JwxtService
) : BaseService {

    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    companion object {
        const val CATEGORY_BASIC_INFO = "基本信息"
        const val CATEGORY_CONTACT_INFO = "联系信息"
    }


    suspend fun getStudentProfile(): ServiceResult<Map<String, Map<String, String>>> = withContext(Dispatchers.IO) {
        Timber.d("StuInfoService: 开始获取并解析学生信息 (按类别)...")

        val htmlBody = service.getProfileHtml()
            .onError { msg, _ -> Timber.e("StuInfoService: 获取学生信息 HTML 失败: $msg") }
            .getOrNull() ?: return@withContext ServiceResult.Error("获取学生信息页面失败")

        if (htmlBody.isBlank()) {
            Timber.e("StuInfoService: HTML 内容为空")
            return@withContext ServiceResult.Error("学生信息页面内容为空")
        }

        return@withContext try {
            Timber.d("StuInfoService: HTML 获取成功，开始按类别解析学生信息...")

            val document = Jsoup.parse(htmlBody)
            val categorizedDataMap = mutableMapOf<String, Map<String, String>>()

            // 基本信息
            parseStuInfo(document, "div#basic table.table_border", "k", "v")
                .takeIf { it.isNotEmpty() }
                ?.also {
                    categorizedDataMap[CATEGORY_BASIC_INFO] = it
                    Timber.d("StuInfoService: 解析到 ${it.size} 条 '$CATEGORY_BASIC_INFO'。")
                }

            // 联系信息
            parseStuInfo(document, "table#view_basic", "k_l", "v_l")
                .takeIf { it.isNotEmpty() }
                ?.also {
                    categorizedDataMap[CATEGORY_CONTACT_INFO] = it
                    Timber.d("StuInfoService: 解析到 ${it.size} 条 '$CATEGORY_CONTACT_INFO'。")
                }

            if (categorizedDataMap.isEmpty()) {
                Timber.w("StuInfoService: 页面未能解析出任何分类的学生信息。")
            } else {
                Timber.i("StuInfoService: 成功解析 ${categorizedDataMap.size} 个信息分类。")
            }

            ServiceResult.Success(categorizedDataMap)

        } catch (e: ParseException) {
            Timber.e(e, "解析学生信息失败: ${e.message}")
            Timber.v("HTML内容片段:\n${htmlBody.take(1000)}")
            ServiceResult.Error("解析学生信息失败: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "解析学生信息时发生未知错误")
            Timber.v("HTML内容片段:\n${htmlBody.take(1000)}")
            ServiceResult.Error("解析学生信息发生未知错误: ${e.message}", e)
        }
    }


    private fun parseStuInfo(
        document: Document,
        tableSelector: String,
        keyCssClass: String,
        valueCssClass: String
    ): Map<String, String> {
        val infoMap = mutableMapOf<String, String>()
        val table = document.selectFirst(tableSelector)
            ?: run {
                Timber.w("StuInfoService (Revised): 未找到信息表格，选择器: '$tableSelector'")
                return emptyMap()
            }

        val rows = table.select("tr")
        if (rows.isEmpty()) {
            Timber.w("StuInfoService (Revised): 找到表格 '$tableSelector'，但其中没有数据行 (tr)。")
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
                        val value = nextCell.text().trim()

                        if (key.isNotEmpty()) {
                            if (key == "电子邮件：") {
                                val emailSpanText = nextCell.selectFirst("span.mail")?.text()?.trim()
                                infoMap[key] = emailSpanText ?: value
                            } else {
                                infoMap[key] = value
                            }
                            i += 2
                        } else {
                            Timber.v("StuInfoService (Revised): Skipped pair in '$tableSelector' due to blank key. Cell HTML: ${currentCell.html()}")
                            i += 2
                        }
                    } else {
                        Timber.w("StuInfoService (Revised): Row in '$tableSelector', key cell '${keyCssClass}' found at end of row. Key Cell HTML: ${currentCell.html()}")
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