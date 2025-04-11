package com.lonx.ecjtu.pda.service // Or your appropriate package

import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.ServiceResult
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

        when (val htmlResult = service.getStudentInfoHtml()) {
            is ServiceResult.Success -> {
                val htmlBody = htmlResult.data
                if (htmlBody.isBlank()) {
                    Timber.e("StuInfoService: 获取学生信息 HTML 成功，但内容为空。")
                    return@withContext ServiceResult.Error("学生信息页面内容为空")
                }

                try {
                    Timber.d("StuInfoService: HTML 获取成功，开始按类别解析学生信息...")
                    val document = Jsoup.parse(htmlBody)
                    val categorizedDataMap = mutableMapOf<String, Map<String, String>>()

                    val basicInfoMap = parseStuInfo(
                        document = document,
                        tableSelector = "div#basic table.table_border",
                        keyCssClass = "k",
                        valueCssClass = "v"
                    )
                    if (basicInfoMap.isNotEmpty()) {
                        categorizedDataMap[CATEGORY_BASIC_INFO] = basicInfoMap
                        Timber.d("StuInfoService: 解析到 ${basicInfoMap.size} 条 '$CATEGORY_BASIC_INFO'。")
                    } else {
                        Timber.w("StuInfoService: 未能解析到 '$CATEGORY_BASIC_INFO'。")
                    }

                    val contactInfoMap = parseStuInfo(
                        document = document,
                        tableSelector = "table#view_basic",
                        keyCssClass = "k_l",
                        valueCssClass = "v_l"
                    )
                    if (contactInfoMap.isNotEmpty()) {
                        categorizedDataMap[CATEGORY_CONTACT_INFO] = contactInfoMap
                        Timber.d("StuInfoService: 解析到 ${contactInfoMap.size} 条 '$CATEGORY_CONTACT_INFO'。")
                    } else {
                        Timber.w("StuInfoService: 未能解析到 '$CATEGORY_CONTACT_INFO'。")
                    }

                    if (categorizedDataMap.isEmpty()) {
                        Timber.w("StuInfoService: 未能从页面解析出任何分类的学生信息。")
                    } else {
                        Timber.i("StuInfoService: 学生信息按类别解析成功，找到 ${categorizedDataMap.size} 个类别。")
                    }

                    return@withContext ServiceResult.Success(categorizedDataMap)

                } catch (e: ParseException) {
                    Timber.e(e, "StuInfoService: 按类别解析学生信息 HTML 时出错: ${e.message}")
                    Timber.v("StuInfoService: 解析失败的 HTML (前 1000 字符):\n${htmlBody.take(1000)}")
                    return@withContext ServiceResult.Error("解析学生信息失败: ${e.message}", e)
                } catch (e: Exception) {
                    Timber.e(e, "StuInfoService: 按类别解析学生信息时发生意外错误")
                    Timber.v("StuInfoService: 解析失败的 HTML (前 1000 字符):\n${htmlBody.take(1000)}")
                    return@withContext ServiceResult.Error("解析学生信息时发生未知错误: ${e.message}", e)
                }
            }
            is ServiceResult.Error -> {
                Timber.e("StuInfoService: 获取学生信息 HTML 失败: ${htmlResult.message}")
                return@withContext ServiceResult.Error("获取学生信息页面失败: ${htmlResult.message}")
            }
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