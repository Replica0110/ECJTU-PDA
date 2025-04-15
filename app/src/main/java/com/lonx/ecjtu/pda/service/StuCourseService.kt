package com.lonx.ecjtu.pda.service

import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.StuCourse
import com.lonx.ecjtu.pda.data.StuDayCourses
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.log
import com.lonx.ecjtu.pda.data.mapCatching
import com.lonx.ecjtu.pda.data.onError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import timber.log.Timber
import java.io.IOException

class StuCourseService(
    private val service: JwxtService
) : BaseService {

    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /**
     * 获取指定日期的课程安排。
     * @param dateQuery 可选的日期查询参数。如果为 null，则获取当天的课表。
     * @return ServiceResult<StuDayCourses> 包含查询日期和当天课程列表的结果。
     *         如果请求成功但页面显示无课程安排，则返回包含日期和空课程列表的 Success。
     *         如果请求失败或 HTML 解析失败，则返回 Error。
     */
    suspend fun getCourseSchedule(dateQuery: String? = null): ServiceResult<StuDayCourses> = withContext(Dispatchers.IO) {
        service.getCourseScheduleHtml(dateQuery)
            .mapCatching { html ->
                parseDayCourses(html)
            }
            .onError { message, exception ->
                Timber.e(exception, "获取或解析课程表失败: $message")
            }
    }

    /**
     * 从 HTML 字符串中解析单日课程安排 (StuDayCourses)。
     * @param html 包含课程安排的 HTML 字符串。
     * @return [StuDayCourses] 包含解析出的日期和课程列表。
     * @throws ParseException 如果 HTML 结构不符合预期，导致关键信息无法解析。
     * @throws Exception 其他潜在的解析错误 (例如 Jsoup 内部错误)。
     */
    @Throws(ParseException::class, Exception::class)
    private fun parseDayCourses(html: String): StuDayCourses {
        val document: Document = Jsoup.parse(html)

        val dateElement = document.selectFirst("div.center > p")
        val extractedDate = if (dateElement != null) {
            dateElement.text().trim().also {
                Timber.d("成功解析日期: $it")
            }
        } else {
            Timber.w("在 HTML 中未找到日期信息 (选择器: div.center > p)。可能当天无课或页面结构变化。")
            "日期未知"
        }

        val courseListElement = document.selectFirst("div.calendar ul.rl_info")
        if (courseListElement == null) {
            Timber.e("关键错误：未找到课程列表容器 (div.calendar ul.rl_info)。HTML 结构可能已更改。")
            throw ParseException("无法找到课程列表容器 (ul.rl_info)，无法继续解析。")
        }

        if (courseListElement.selectFirst("li > p > img") != null) {
            Timber.i("检测到无课标记 (li > p > img)。日期: $extractedDate")
            return StuDayCourses(extractedDate, emptyList())
        }

        val courses = mutableListOf<StuCourse>()
        val listItems = courseListElement.select("li")

        if (listItems.isEmpty() && courseListElement.selectFirst("li > p > img") == null) {
            Timber.i("课程列表容器存在，但内部没有课程项 (li) 且没有无课标记。视为无课。日期: $extractedDate")
            return StuDayCourses(extractedDate, emptyList())
        }


        for (item in listItems) {
            val pElement = item.selectFirst("p")
            if (pElement == null) {
                Timber.w("跳过缺少 'p' 标签的列表项: ${item.outerHtml()}")
                continue
            }

            val lines = Jsoup.clean(pElement.html(), "", Safelist.none().addTags("br"))
                .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (lines.isEmpty()) {
                Timber.w("列表项 'p' 标签内解析不出任何有效文本行: ${pElement.outerHtml()}")
                continue
            }

            var weeksRaw = "周次未知"
            var sectionsRaw = "节次未知"
            var location = "地点未知"
            var teacher = "教师未知"
            var courseName = "课程名未知"
            var courseNameFound = false

            val spanContentText = pElement.selectFirst("span.class_span")?.text()?.trim() ?: ""


            for (line in lines) {
                when {
                    line.startsWith("时间：") -> {
                        val timePart = line.substringAfter("时间：").trim()
                        val parts = timePart.split(Regex("\\s+"), limit = 2)
                        weeksRaw = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: weeksRaw
                        sectionsRaw = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: sectionsRaw
                    }
                    line.startsWith("地点：") -> {
                        location = line.substringAfter("地点：").trim().takeIf { it.isNotBlank() } ?: location
                    }
                    line.startsWith("教师：") -> {
                        teacher = line.substringAfter("教师：").trim().takeIf { it.isNotBlank() } ?: teacher
                    }
                    !line.startsWith("时间：") && !line.startsWith("地点：") && !line.startsWith("教师：") && line != spanContentText && !courseNameFound -> {
                        courseName = line // 假定这一行是课程名称
                        courseNameFound = true
                        Timber.v("识别到课程名称: '$courseName'")
                    }
                    !courseNameFound -> {
                    }
                    else -> {
                        Timber.d("忽略或未识别的行: '$line' in ${pElement.outerHtml()}")
                    }
                }
            }

            if (!courseNameFound || courseName == "课程名未知") {
                Timber.w("未能从行中明确识别课程名称: lines=$lines, HTML=${pElement.outerHtml()}")
            }


            courses.add(
                StuCourse(
                    courseName = courseName,
                    sectionsRaw = "节次：${sectionsRaw}",
                    weeksRaw = "上课周：${weeksRaw}",
                    location = "地点：${location}",
                    teacher = "教师：${teacher}"
                )
            )
        }


        if (listItems.isNotEmpty() && courses.isEmpty()) {
            Timber.w("处理了 ${listItems.size} 个列表项，但未能成功解析任何课程。请检查解析逻辑和 HTML 结构。日期: $extractedDate")
        }

        Timber.i("为日期 $extractedDate 解析到 ${courses.size} 门课程")
        return StuDayCourses(extractedDate, courses)
    }
}
