package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.common.log
import com.lonx.ecjtu.pda.data.common.mapCatching
import com.lonx.ecjtu.pda.data.common.onError
import com.lonx.ecjtu.pda.data.common.onSuccess
import com.lonx.ecjtu.pda.data.model.StuAllSchedules
import com.lonx.ecjtu.pda.data.model.StuCourse
import com.lonx.ecjtu.pda.data.model.TermInfo
import com.lonx.ecjtu.pda.data.model.TermSchedules
import com.lonx.ecjtu.pda.data.model.WeekDay
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.repository.SchedulesRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import timber.log.Timber
import java.io.IOException

class JwxtSchedulesRepositoryImpl(
    apiClient: JwxtApiClient,
    authRepository: AuthRepository
) : BaseJwxtRepository(apiClient, authRepository), SchedulesRepository {

    override suspend fun getAllSchedules(): PDAResult<StuAllSchedules> = withContext(Dispatchers.IO) {
        Timber.d("开始获取所有学期课表...")

        val allSchedules = mutableListOf<TermSchedules>()
        val failedTerms = mutableListOf<Pair<String, String>>()

        // 获取初始页面并解析
        val initialResult = getHtml { apiClient.getScheduleHtml() }
            .log("Initial Fetch")
            .mapCatching { html ->
                val doc = Jsoup.parse(html)
                val terms = parseTermInfoListFromDoc(doc)
                if (terms.isEmpty()) throw IOException("未能解析学期列表")
                val initialSchedule = runCatching {
                    parseScheduleFromDoc(doc)
                }.onFailure { e ->
                    val termValue = doc.selectFirst("select#term option[selected]")?.`val`() ?: "default"
                    val msg = "解析默认学期课表失败: ${e.message}"
                    Timber.w(e, msg)
                    failedTerms.add(termValue to msg)
                }.getOrNull()
                Triple(terms, initialSchedule, doc)
            }

        val (termList, initialSchedule, _) = when (initialResult) {
            is PDAResult.Success -> initialResult.data
            is PDAResult.Error -> return@withContext PDAResult.Error(
                "课表加载失败: ${initialResult.message}", initialResult.exception
            )
        }

        val initialTermValue = initialSchedule?.termValue
        initialSchedule?.let { allSchedules.add(it) }

        for (term in termList) {
            if (term.value == initialTermValue) continue

            getHtml { apiClient.getScheduleHtml(term.value) }
                .log("Fetch ${term.value}")
                .mapCatching { html ->
                    val doc = Jsoup.parse(html)
                    parseScheduleFromDoc(doc, expectedTermValue = term.value)
                }
                .onSuccess { schedule ->
                    allSchedules.add(schedule)
                    Timber.i("成功解析学期 ${term.name} 的课表")
                }
                .onError { msg, e ->
                    Timber.e(e, "获取或解析学期 ${term.value} 失败: $msg")
                    failedTerms.add(term.value to msg)
                }
        }

        return@withContext if (allSchedules.isNotEmpty()) {
            if (failedTerms.isNotEmpty()) {
                Timber.w("部分失败: $failedTerms")
            }
            PDAResult.Success(StuAllSchedules(allSchedules.distinctBy { it.termValue }))
        } else {
            val msg = "全部失败，错误信息: ${failedTerms.joinToString { "${it.first}: ${it.second}" }}"
            Timber.e(msg)
            PDAResult.Error(msg)
        }
    }


    /**
     * 从 Jsoup Document 中解析学期信息列表。
     * @throws ParseException 如果无法找到或解析必要的元素。
     */
    @Throws(ParseException::class)
    private fun parseTermInfoListFromDoc(doc: Document): List<TermInfo> {
        val termSelect = doc.selectFirst("select#term")
            ?: throw ParseException("在文档中找不到学期选择元素 (select#term)。")
        val options = termSelect.select("option")
        if (options.isEmpty()) {
            Timber.w("找到学期选择元素，但其中不包含任何选项 (option)。")
            return emptyList()
        }
        return options.mapNotNull { option ->
            val value = option.`val`()
            val name = option.text()
            if (value.isNotEmpty() && name.isNotEmpty()) {
                TermInfo(value, name)
            } else {
                Timber.w("跳过缺少值或名称的学期选项: ${option.outerHtml()}")
                null
            }
        }
    }

    /**
     * 从 Jsoup Document 中解析单个学期的课表 (TermSchedules)。
     * @param doc Jsoup 文档对象。
     * @param expectedTermValue 可选，用于验证解析出的学期是否与预期一致。
     * @throws ParseException 如果无法找到或解析必要的元素（如课表）。
     */
    @Throws(ParseException::class)
    private fun parseScheduleFromDoc(doc: Document, expectedTermValue: String? = null): TermSchedules {
        // 1. 解析学期信息
        val termSelect = doc.selectFirst("select#term")
            ?: throw ParseException("在课表文档中找不到学期选择元素。")
        val selectedOption = termSelect.selectFirst("option[selected]")
            ?: termSelect.selectFirst("option")
            ?: throw ParseException("在课表文档中找不到选中的学期选项。")
        val actualTermValue = selectedOption.`val`()
        val actualTermName = selectedOption.text()

        // 可选的学期验证
        if (expectedTermValue != null && actualTermValue != expectedTermValue) {
            Timber.w("请求的学期是 '$expectedTermValue', 但加载的页面显示选中的学期是 '$actualTermValue'。")
        }

        // 解析课程表格
        val parsedCourses = mutableListOf<StuCourse>()
        val courseTable = doc.selectFirst("table#courseSche")
        // 更具体的错误消息
            ?: throw ParseException("找不到学期 '$actualTermValue' ($actualTermName) 的课程表元素 (table#courseSche)。")
        val rows = courseTable.select("tr:gt(0)") // 跳过表头行

        if (rows.isEmpty()) {
            Timber.w("课程表 (table#courseSche) 中没有找到任何课程行 (tr)。学期: $actualTermName")
            // 表格存在但没有数据行，返回空课程列表，而不是抛异常
        }

        for (row in rows) { // 遍历时间行
            val cells = row.select("td")
            if (cells.size < 2) continue // 至少需要时间单元格 + 1天

            val timeSlot = cells[0].text().trim()
            // 改进时间槽验证，确保是有效的上课时间描述
            if (timeSlot.isEmpty() || !timeSlot.contains(Regex("\\d"))) {
                Timber.v("跳过无效或非标准的时间行: '$timeSlot'")
                continue
            }

            for (dayIndex in 1 until cells.size.coerceAtMost(8)) {
                val day = WeekDay.entries.getOrNull(dayIndex - 1) ?: continue
                val cell = cells[dayIndex]

                val cellContent = Jsoup.clean(cell.html(), "", Safelist.none().addTags("br"))
                    .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                    .replace(" ", " ")
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")

                if (cellContent.isBlank()) continue

                val lines = cellContent.lines()
                var i = 0
                while (i < lines.size) {
                    val courseName = lines[i]
                    var teacher = "N/A"
                    var location = "N/A"
                    var weeksRaw = "N/A"
                    var sectionsRaw = "N/A"
                    var consumedLines = 1

                    var blockEndLineIndex = -1
                    for (j in i until lines.size) {
                        // 周数(单双周) 节数 的正则，更健壮地匹配行尾
                        if (Regex("""([\d\-]+(?:\s*\([单双]\))?周?)\s+([\d,\-]+)节?$""").containsMatchIn(lines[j])) {
                            blockEndLineIndex = j
                            break
                        }
                        // 如果下一行看起来像新的课程名称，则结束当前块
                        if (j > i && lines[j].isNotBlank() &&
                            !lines[j].contains("@") && // 不含地点标记
                            !Regex("""\d.*\d""").containsMatchIn(lines[j]) &&
                            !lines[j].contains(Regex("周|节")) &&
                            lines[j].length < 30) { // 简单的长度限制
                            blockEndLineIndex = j - 1
                            break
                        }
                    }
                    if (blockEndLineIndex == -1) {
                        blockEndLineIndex = lines.size - 1
                    }

                    val blockLines = lines.subList(i, blockEndLineIndex + 1)
                    consumedLines = blockLines.size

                    if (blockLines.isNotEmpty()) {
                        val lastLine = blockLines.last()
                        // 尝试从最后一行提取周数和节数
                        val weekSecRegex = Regex("""^(.*?)\s*([\d,\-\s()（）单双]+周?)\s+([\d,\-]+)节?$""")
                        val weekSecMatch = weekSecRegex.find(lastLine)

                        if (weekSecMatch != null) {
                            val remainingLastLine = weekSecMatch.groupValues[1].trim()
                            weeksRaw = weekSecMatch.groupValues[2].trim()
                            sectionsRaw = weekSecMatch.groupValues[3].trim()

                            // 整合最后一行之前的内容 + 最后一行剩余部分，用于提取教师和地点
                            val teacherLocString = (blockLines.dropLast(1) + remainingLastLine)
                                .joinToString(" ")
                                .replace(courseName, "") // 避免重复课程名
                                .trim()

                            // 提取教师和地点
                            val locMatch = Regex("""@(.*)""").find(teacherLocString)
                            if (locMatch != null) {
                                location = locMatch.groupValues[1].trim()
                                teacher = teacherLocString.substringBeforeLast('@').trim()
                            } else {
                                teacher = teacherLocString // 没有'@'则假定都是教师名
                            }

                            // 清理教师和地点字段
                            teacher = teacher.ifBlank { "N/A" }
                            location = location.ifBlank { "N/A" }


                            val course = StuCourse(
                                courseName = courseName,
                                teacher = teacher,
                                location = location,
                                weeksRaw = weeksRaw,
                                sectionsRaw = sectionsRaw,
                                day = day,
                                timeSlot = timeSlot
                            )
                            parsedCourses.add(course)
                        } else {
                            Timber.w("无法从最后一行解析周数/节数: '$lastLine' (块: $blockLines)。单元格: $day $timeSlot, 学期: $actualTermName")
                        }
                    }
                    i += consumedLines // 移动到下一个潜在课程块
                }
            }
        }

        return TermSchedules(
            termValue = actualTermValue,
            termName = actualTermName,
            courses = parsedCourses
        )
    }
}
