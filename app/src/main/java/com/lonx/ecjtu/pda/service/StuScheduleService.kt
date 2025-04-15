package com.lonx.ecjtu.pda.service

import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.TermSchedules
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.StuCourse
import com.lonx.ecjtu.pda.data.TermInfo
import com.lonx.ecjtu.pda.data.WeekDay
import com.lonx.ecjtu.pda.data.log
import com.lonx.ecjtu.pda.data.mapCatching
import com.lonx.ecjtu.pda.data.onError
import com.lonx.ecjtu.pda.data.onSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import timber.log.Timber
import java.io.IOException

class StuScheduleService(
    private val service: JwxtService
) : BaseService {

    // 解析异常类保持不变
    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /**
     * 获取所有学期的完整课表。
     * 此方法会为每个学期发起一次网络请求，可能耗时较长。
     *
     * @return ServiceResult<List<TermSchedules>> 包含所有成功获取的学期课表列表。
     *         如果任何学期的获取或解析失败，它们将被跳过。
     *         如果初始获取学期列表失败，或所有学期都获取/解析失败，则返回 Error。
     */
    suspend fun getAllSchedules(): ServiceResult<List<TermSchedules>> = withContext(Dispatchers.IO) {
        Timber.d("开始获取所有学期课表...")
        val allSuccessfullyParsedSchedules = mutableListOf<TermSchedules>()
        val failedTermDetails = mutableListOf<Pair<String, String>>() // (termValue, reason)

        // 1. 获取初始页面并解析学期列表和初始课表
        val initialFetchResult = service.getScheduleHtml(term = null)
            .log("Initial Fetch") // 记录初始请求结果
            .mapCatching { html ->
                val doc = Jsoup.parse(html)
                val terms = parseTermInfoListFromDoc(doc) // 可能抛出 ParseException
                if (terms.isEmpty()) {
                    throw ParseException("在初始页面未找到任何学期信息。")
                }
                // 尝试解析初始课表，如果失败则记录并返回 null
                val initialSchedule = try {
                    parseScheduleFromDoc(doc) // 可能抛出 ParseException
                } catch (e: Exception) {
                    val termValue = doc.selectFirst("select#term option[selected]")?.`val`() ?: "default"
                    val reason = "解析初始默认课表失败 (Term: $termValue): ${e.message}"
                    Timber.w(e, reason)
                    failedTermDetails.add(termValue to reason)
                    null // 表示初始课表解析失败
                }
                doc to Pair(terms, initialSchedule) // 返回 Document, 学期列表 和 可能为 null 的初始课表
            }

        // 处理初始请求的结果
        val allTermInfos: List<TermInfo>
        var initialParsedTermValue: String? = null

        when (initialFetchResult) {
            is ServiceResult.Success -> {
                val (_, parsedData) = initialFetchResult.data
                val (terms, initialSchedule) = parsedData
                allTermInfos = terms
                initialSchedule?.let { // 如果初始课表解析成功
                    allSuccessfullyParsedSchedules.add(it)
                    initialParsedTermValue = it.termValue
                    Timber.i("成功解析初始课表: ${it.termName}")
                }
            }
            is ServiceResult.Error -> {
                // 初始请求或解析学期列表失败，直接返回错误
                Timber.e("获取或解析初始页面/学期列表失败: ${initialFetchResult.message}")
                return@withContext ServiceResult.Error("获取或解析初始页面/学期列表失败: ${initialFetchResult.message}", initialFetchResult.exception)
            }
        }

        // 2. 遍历获取并解析其他学期的课表
        for (termInfo in allTermInfos) {
            // 如果初始课表已解析且当前学期是初始学期，则跳过
            if (termInfo.value == initialParsedTermValue) {
                Timber.d("跳过已处理的初始学期: ${termInfo.name}")
                continue
            }

            Timber.d("正在处理学期: ${termInfo.name} (${termInfo.value})")

            service.getScheduleHtml(term = termInfo.value)
                .log("Fetch Schedule ${termInfo.value}") // 记录请求结果
                .mapCatching { html ->
                    // 在 mapCatching 中解析，捕获 Jsoup 或 parseScheduleFromDoc 的异常
                    val specificDoc = Jsoup.parse(html)
                    parseScheduleFromDoc(specificDoc, expectedTermValue = termInfo.value) // 可能抛出 ParseException
                }
                .onSuccess { schedule ->
                    // 成功时添加到列表
                    allSuccessfullyParsedSchedules.add(schedule)
                    Timber.i("成功解析学期 ${termInfo.name} 的课表")
                }
                .onError { message, exception ->
                    // 失败时记录错误详情
                    val reason = if (exception is ParseException) {
                        "解析失败: ${exception.message}"
                    } else {
                        "获取或处理失败: $message"
                    }
                    Timber.e(exception, "处理学期 ${termInfo.value} 时出错: $reason")
                    failedTermDetails.add(termInfo.value to reason)
                }
        }

        // 3. 根据结果返回最终的 ServiceResult
        if (allSuccessfullyParsedSchedules.isNotEmpty()) {
            if (failedTermDetails.isNotEmpty()) {
                Timber.w("部分学期未能成功获取或解析: $failedTermDetails")
                // 注意：即使有失败，只要至少有一个成功，我们仍然返回 Success
            }
            // 去重，以防初始课表和列表中的第一个是同一个学期
            ServiceResult.Success(allSuccessfullyParsedSchedules.distinctBy { it.termValue })
        } else {
            // 所有学期都失败了（包括初始页面可能就失败了）
            val errorMsg = "未能成功获取或解析任何学期的课表。遇到的错误: ${failedTermDetails.joinToString { "${it.first}: ${it.second}" }}"
            Timber.e(errorMsg)
            ServiceResult.Error(errorMsg)
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

        // 返回包含学期信息和课程列表的 TermSchedules
        return TermSchedules(
            termValue = actualTermValue,
            termName = actualTermName,
            courses = parsedCourses
        )
    }
}