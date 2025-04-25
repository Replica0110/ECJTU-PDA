package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.common.log
import com.lonx.ecjtu.pda.data.common.mapCatching
import com.lonx.ecjtu.pda.data.common.onError
import com.lonx.ecjtu.pda.data.model.CourseScore
import com.lonx.ecjtu.pda.data.model.RequirementCredits
import com.lonx.ecjtu.pda.data.model.ScoreSummary
import com.lonx.ecjtu.pda.data.model.StuAllScores
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.repository.ScoreRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException
import java.util.regex.Pattern

class JwxtScoreRepositoryImpl(
    apiClient: JwxtApiClient,
    authRepository: AuthRepository
): BaseJwxtRepository(apiClient, authRepository), ScoreRepository
{
    override suspend fun getStudentScores(): PDAResult<StuAllScores> = withContext(Dispatchers.IO) {
        Timber.d("JwxtScoreRepository: 开始获取并解析成绩数据...")

        fetchHtmlWithRelogin{
            apiClient.getStudentScoresHtml()
                .onError { msg, _ -> Timber.e("JwxtScoreRepository: 获取成绩 HTML 失败: $msg") } }
            .mapCatching { htmlBody ->
                if (htmlBody.isBlank()) throw IOException("成绩页面内容为空")

                Timber.d("JwxtScoreRepository: HTML 获取成功，开始解析成绩数据...")
                val document = Jsoup.parse(htmlBody)

                val summary = parseScoreSummaryTable(document)
                val detailedScores = parseDetailedScores(document)

                Timber.i(
                    "JwxtScoreRepository: 成绩数据解析成功。" +
                            "摘要字段数: ${summary.javaClass.declaredFields.count { it.type == RequirementCredits::class.java || it.name.startsWith("gpa") || it.name.startsWith("academicWarning") }}，" +
                            "详细成绩条目数: ${detailedScores.size}"
                )

                StuAllScores(summary, detailedScores)
            }
            .onError { msg, e ->
                Timber.e(e, "JwxtScoreRepository: 解析成绩数据失败: $msg")
                e?.let { Timber.v("HTML 内容片段:\n${it.message ?: "无"}") }
            }
            .log( "")
    }

    /**
     * Parses the score summary table (div.score-count > table).
     */
    private fun parseScoreSummaryTable(document: Document): ScoreSummary {
        val summaryTable = document.selectFirst("div.score-count > table.table_border.personal-socre-tab")
            ?: throw ParseException("无法找到成绩摘要表格 (div.score-count > table)")
        val dataRow = summaryTable.select("tr").lastOrNull { it.selectFirst("td") != null }
            ?: throw ParseException("无法找到成绩摘要表格的数据行 (td)")
        val cells = dataRow.select("td")

        val offset = summaryTableOffset(summaryTable)

        fun parseDoubleFromCell(index: Int): Double? {
            return cells.getOrNull(offset + index)?.text()?.trim()?.toDoubleOrNull()
        }

        fun parseDoubleFromCellReq(index: Int): Double {
            return cells.getOrNull(offset + index)?.text()?.trim()?.toDoubleOrNull() ?: 0.0
        }

        try {
            val academicWarningRequired = parseDoubleFromCell(0)
            val academicWarningCompleted = parseDoubleFromCell(1)
            val gpaRequired = parseDoubleFromCell(2)
            val gpaAchieved = parseDoubleFromCell(3)
            val publicElective = RequirementCredits(parseDoubleFromCellReq(4), parseDoubleFromCellReq(5), parseDoubleFromCellReq(6))
            val mgmtLawElective = RequirementCredits(parseDoubleFromCellReq(7), parseDoubleFromCellReq(8), parseDoubleFromCellReq(9))
            val humanitiesArtElective = RequirementCredits(parseDoubleFromCellReq(10), parseDoubleFromCellReq(11), parseDoubleFromCellReq(12))
            val scienceTechElective = RequirementCredits(parseDoubleFromCellReq(13), parseDoubleFromCellReq(14), parseDoubleFromCellReq(15))
            val healthElective = RequirementCredits(parseDoubleFromCellReq(16), parseDoubleFromCellReq(17), parseDoubleFromCellReq(18))
            val disciplineElective = RequirementCredits(parseDoubleFromCellReq(19), parseDoubleFromCellReq(20), parseDoubleFromCellReq(21))
            val majorElective = RequirementCredits(parseDoubleFromCellReq(22), parseDoubleFromCellReq(23), parseDoubleFromCellReq(24))

            return ScoreSummary(
                academicWarningRequired, academicWarningCompleted, gpaRequired, gpaAchieved,
                publicElective, mgmtLawElective, humanitiesArtElective, scienceTechElective,
                healthElective, disciplineElective, majorElective
            )
        } catch (e: IndexOutOfBoundsException) {
            throw ParseException(
                "解析摘要表格时单元格索引越界 (偏移 $offset，总 ${cells.size} 个单元格)",
                e
            )
        } catch (e: NumberFormatException) {
            throw ParseException(
                "解析摘要表格时数字格式错误",
                e
            )
        }
    }


    /**
     * Parses the detailed course score list (div.s_termScore > ul.term_score).
     */
    private fun parseDetailedScores(document: Document): List<CourseScore> {
        val scoreContainer = document.selectFirst("div.score-detail > div.s_termScore")
            ?: run {
                Timber.w("JwxtScoreRepository: 未找到详细成绩容器 (div.score-detail > div.s_termScore)，返回空列表。")
                return emptyList()
            }
        val scoreRows = scoreContainer.select("ul.term_score")
        val detailedScores = mutableListOf<CourseScore>()
        val courseIdPattern = Pattern.compile("【(\\d+?)】")


        for (row in scoreRows) {
            val listItems = row.select("li")
            if (listItems.size < 8) {
                Timber.w("JwxtScoreRepository: 跳过一个成绩行，因为单元格数量不足 (预期 8，实际 ${listItems.size})")
                continue
            }
            try {
                fun getTextFromLi(index: Int): String { return listItems.getOrNull(index)?.text()?.trim() ?: "" }
                fun getNullableTextFromLi(index: Int): String? { return listItems.getOrNull(index)?.text()?.trim()?.takeIf { it.isNotEmpty() } }
                fun parseDoubleFromLi(index: Int): Double { return getTextFromLi(index).toDoubleOrNull() ?: 0.0 }

                val term = getTextFromLi(0)
                val rawCourseName = getTextFromLi(1)
                val requirementType = getTextFromLi(2)
                val assessmentMethod = getTextFromLi(3)
                val credits = parseDoubleFromLi(4)
                val score = getNullableTextFromLi(5)
                val retakeScore = getNullableTextFromLi(6)
                val resitScore = getNullableTextFromLi(7)

                val matcher = courseIdPattern.matcher(rawCourseName)
                val courseId = if (matcher.find()) matcher.group(1) else null
                val courseName = rawCourseName.replace(courseIdPattern.toRegex(), "").trim()

                if (term.isBlank() || courseName.isBlank()) {
                    Timber.w("JwxtScoreRepository: 跳过一个成绩行，因为学期或课程名称为空。Raw Name: '$rawCourseName'")
                    continue
                }

                detailedScores.add(
                    CourseScore(
                        term, courseId, courseName, requirementType, assessmentMethod,
                        credits, score, retakeScore, resitScore
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "JwxtScoreRepository: 解析单个成绩行时出错。HTML: ${row.html().take(200)}")
                throw ParseException(
                    "解析详细成绩列表中的行时出错: ${e.message}",
                    e
                )
            }
        }
        return detailedScores
    }
    private fun summaryTableOffset(table: Element): Int {
        val headerRows = table.select("tr").take(3)
        val thTexts = headerRows.flatMap { it.select("th").map { th -> th.text().trim() } }

        // 想要跳过的字段
        val skipHeaders = listOf("班级", "姓名", "学籍状态")

        // 计算前缀中连续出现了多少个 skipHeaders（即我们要跳过几列）
        var offset = 0
        for (header in thTexts) {
            if (header in skipHeaders) offset++ else break
        }

        Timber.d("解析成绩摘要时识别到前置跳过列数: $offset")
        return offset
    }

}