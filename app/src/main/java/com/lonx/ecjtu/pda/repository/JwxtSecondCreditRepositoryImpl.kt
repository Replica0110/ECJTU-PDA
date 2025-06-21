package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.common.getOrNull
import com.lonx.ecjtu.pda.data.common.onError
import com.lonx.ecjtu.pda.data.model.StuSecondCredits
import com.lonx.ecjtu.pda.data.model.YearlySecondCredits
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.repository.SecondCreditRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException
import java.util.regex.Pattern

class JwxtSecondCreditRepositoryImpl(
    apiClient: JwxtApiClient,
    authRepository: AuthRepository
) : BaseJwxtRepository(apiClient, authRepository), SecondCreditRepository {

    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private val yearPattern: Pattern = Pattern.compile("(\\d{4})\\s+学年学分记录")

    override suspend fun getSecondCredit(): PDAResult<StuSecondCredits> = withContext(Dispatchers.IO) {
        Timber.d("JwxtStuSecondCreditRepositoryImpl: 开始获取并解析素质拓展学分数据...")

        try {
            val htmlBodyResult = getHtml { apiClient.getSecondCreditHtml() }
                .onError { msg, e -> Timber.e(e, "获取素质拓展学分 HTML 失败: $msg") }

            val htmlBody = htmlBodyResult.getOrNull()
                ?: return@withContext PDAResult.Error("无法获取素质拓展学分页面")

            if (htmlBody.isBlank()) {
                return@withContext PDAResult.Error("素质拓展学分页面内容为空")
            }

            val document = Jsoup.parse(htmlBody)
            val container = document.selectFirst("div#data-center")
                ?: throw ParseException("找不到主要数据容器 div#data-center")

            val parsedData = parseTables(container)
            Timber.i("JwxtStuSecondCreditRepositoryImpl: 解析成功，总类数: ${parsedData.totalCreditsByCategory.size}，学年数: ${parsedData.yearlyCredits.size}")

            PDAResult.Success(parsedData)
        } catch (e: ParseException) {
            Timber.e(e, "解析失败: ${e.message}")
            PDAResult.Error("解析素质拓展学分失败", e)
        } catch (e: Exception) {
            Timber.e(e, "未知错误")
            PDAResult.Error("解析素质拓展学分时发生未知错误", e)
        }
    }

    private fun parseTables(container: Element): StuSecondCredits {
        val children = container.children()
        val yearlyCredits = mutableListOf<YearlySecondCredits>()
        var totalCredits = emptyMap<String, Double>()

        var i = 0
        while (i < children.size) {
            val element = children[i]
            if (element.tagName() == "h3") {
                val heading = element.text()
                val table = children.getOrNull(i + 1)?.takeIf { it.tagName() == "table" }

                if (table != null) {
                    if (heading.contains("第二课堂总学分")) {
                        totalCredits = parseCreditTable(table)
                        i += 2
                    } else {
                        val matcher = yearPattern.matcher(heading)
                        if (matcher.find()) {
                            val year = matcher.group(1) ?: ""
                            val creditsMap = parseCreditTable(table)
                            yearlyCredits.add(YearlySecondCredits(year, creditsMap))
                            i += 2
                        } else {
                            Timber.w("无法识别 h3 标题中的学年: $heading")
                            i++
                        }
                    }
                } else {
                    Timber.w("h3 后缺少 table 元素: $heading")
                    i++
                }
            } else {
                i++
            }
        }

        return StuSecondCredits(totalCredits, yearlyCredits)
    }

    private fun parseCreditTable(table: Element): Map<String, Double> {
        val headers = table.selectFirst("tr:has(th)")?.select("th") ?: return emptyMap()
        val cells = table.selectFirst("tr:has(td)")?.select("td") ?: return emptyMap()

        val minCols = 2
        val size = minOf(headers.size, cells.size)
        if (size <= minCols) return emptyMap()

        val result = mutableMapOf<String, Double>()
        for (i in minCols until size) {
            val category = headers[i].text().trim()
            val credit = cells[i].text().trim().toDoubleOrNull() ?: 0.0
            if (category.isNotEmpty()) result[category] = credit
        }
        return result
    }
}
