package com.lonx.ecjtu.pda.service

import com.google.gson.annotations.SerializedName
import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.common.getOrNull
import com.lonx.ecjtu.pda.data.common.onError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Represents the extracurricular credits earned in a specific academic year.
 * 代表在特定学年获得的素质拓展学分。
 *
 * @property academicYear The academic year string (e.g., "2023"). 学年字符串。
 * @property creditsByCategory A map where the key is the credit category name (e.g., "文体艺术")
 *                             and the value is the credits earned (Double).
 *                             Map结构，键是学分类别名（如“文体艺术”），值是获得的分数（Double）。
 */
data class YearlySecondCredits(
    @SerializedName("academic_year")
    val academicYear: String = "",

    @SerializedName("credits_by_category")
    val creditsByCategory: Map<String, Double> = emptyMap()
)

/**
 * Container for the overall extracurricular credit data.
 * 包含整体素质拓展学分数据的容器。
 *
 * @property totalCreditsByCategory A map representing the total credits for each category across all years.
 *                                   代表所有学年各类别总学分的Map。
 * @property yearlyCredits A list containing the credit breakdown for each academic year.
 *                          包含各学年学分明细的列表。
 */
data class SecondCreditData(
    @SerializedName("total_credits_by_category")
    val totalCreditsByCategory: Map<String, Double> = emptyMap(),

    @SerializedName("yearly_credits")
    val yearlyCredits: List<YearlySecondCredits> = emptyList()
)



class StuSecondCreditService(
    private val service: JwxtService
):BaseService {
    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private val yearPattern: Pattern = Pattern.compile("(\\d{4})\\s+学年学分记录")

    suspend fun getSecondCredit(): ServiceResult<SecondCreditData> = withContext(Dispatchers.IO) {
        Timber.d("StuSecondCreditService: 开始获取并解析素质拓展学分数据...")

        val htmlBody = service.getSecondCreditHtml()
            .onError { msg, _ -> Timber.e("StuSecondCreditService: 获取 HTML 失败: $msg") }
            .getOrNull() ?: return@withContext ServiceResult.Error("获取素质拓展学分页面失败")

        if (htmlBody.isBlank()) {
            Timber.e("StuSecondCreditService: HTML 内容为空")
            return@withContext ServiceResult.Error("素质拓展学分页面内容为空")
        }

        return@withContext try {
            Timber.d("StuSecondCreditService: HTML 获取成功，开始解析...")
            val document = Jsoup.parse(htmlBody)
            val dataContainer = document.selectFirst("div#data-center")
                ?: throw ParseException("无法找到主数据容器 (div#data-center)")

            val parsedData = parseTables(dataContainer)
            Timber.i("StuSecondCreditService: 数据解析成功。总类别数: ${parsedData.totalCreditsByCategory.size}，学年记录数: ${parsedData.yearlyCredits.size}")
            ServiceResult.Success(parsedData)

        } catch (e: ParseException) {
            Timber.e(e, "StuSecondCreditService: 解析失败: ${e.message}")
            Timber.v("失败 HTML 片段:\n${htmlBody.take(1000)}")
            ServiceResult.Error("解析素质拓展学分失败: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "StuSecondCreditService: 发生未知错误")
            Timber.v("失败 HTML 片段:\n${htmlBody.take(1000)}")
            ServiceResult.Error("解析素质拓展学分时发生未知错误: ${e.message}", e)
        }
    }




    private fun parseTables(container: Element): SecondCreditData {
        var totalCredits = emptyMap<String, Double>()
        val yearlyCreditsList = mutableListOf<YearlySecondCredits>()

        val children = container.children()
        var i = 0
        while (i < children.size) {
            val element = children[i]
            if (element.tagName() == "h3") {
                val headingText = element.text()
                val tableElement = children.getOrNull(i + 1)?.takeIf { it.tagName() == "table" }

                if (tableElement != null) {
                    if (headingText.contains("第二课堂总学分")) {
                        Timber.d(" 解析总学分表格...")
                        totalCredits = parseCreditTable(tableElement)
                        i += 2
                    } else {
                        val yearMatcher: Matcher = yearPattern.matcher(headingText)
                        if (yearMatcher.find()) {
                            val year = yearMatcher.group(1) ?: ""
                            Timber.d("解析 $year 学年学分表格...")
                            val yearlyMap = parseCreditTable(tableElement)
                            yearlyCreditsList.add(YearlySecondCredits(year, yearlyMap))
                            i += 2
                        } else {
                            Timber.w("找到一个 h3 但无法匹配年份或总学分标题: '$headingText'")
                            i++
                        }
                    }
                } else {
                    Timber.w("找到一个 h3 但其后没有 table 元素: '$headingText'")
                    i++ // Skip only h3
                }
            } else {
                i++
            }
        }

        if (totalCredits.isEmpty() && yearlyCreditsList.isEmpty()) {
            Timber.w("未能从页面解析出任何总学分或年度学分数据。")

        }


        return SecondCreditData(totalCredits, yearlyCreditsList)
    }

    /**
     * Parses a single credit table (either total or yearly) into a map.
     * Assumes the first two columns are Name/ID and ignores them.
     */
    private fun parseCreditTable(table: Element): Map<String, Double> {
        val headerRow = table.selectFirst("tr:has(th)")
        val dataRow = table.selectFirst("tr:has(td)")

        if (headerRow == null || dataRow == null) {
            Timber.w("StuExtracurricularService: 表格缺少表头(th)或数据行(td)。 Table HTML: ${table.html().take(150)}")
            return emptyMap()
        }

        val headers = headerRow.select("th")
        val cells = dataRow.select("td")

        val minCols = 2
        if (headers.size <= minCols || cells.size <= minCols) {
            Timber.w("StuExtracurricularService: 表格列数不足 (Headers: ${headers.size}, Cells: ${cells.size})，无法解析学分。")
            return emptyMap()
        }
        if (headers.size != cells.size) {
            Timber.w("StuExtracurricularService: 表头和数据单元格数量不匹配 (Headers: ${headers.size}, Cells: ${cells.size})。将尝试按最小数量解析。")
        }


        val creditsMap = mutableMapOf<String, Double>()
        val commonSize = minOf(headers.size, cells.size)

        for (i in minCols until commonSize) {
            val category = headers[i].text().trim()
            val creditValue = cells[i].text().trim().toDoubleOrNull() ?: 0.0
            if (category.isNotEmpty()) {
                creditsMap[category] = creditValue
            } else {
                Timber.w("StuExtracurricularService: 在索引 $i 处发现空的学分类别名称。")
            }
        }

        return creditsMap
    }

}