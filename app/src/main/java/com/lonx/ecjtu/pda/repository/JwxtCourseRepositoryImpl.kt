package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.common.log
import com.lonx.ecjtu.pda.data.common.mapCatching
import com.lonx.ecjtu.pda.data.common.onError
import com.lonx.ecjtu.pda.data.model.StuCourse
import com.lonx.ecjtu.pda.data.model.StuDayCourses
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.repository.CourseRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import timber.log.Timber
import java.io.IOException


class JwxtCourseRepositoryImpl(
    apiClient: JwxtApiClient,
    authRepository: AuthRepository
) : BaseJwxtRepository(apiClient, authRepository),CourseRepository {

    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    override suspend fun getCoursesForDate(dateQuery: String?): PDAResult<StuDayCourses> = withContext(
        Dispatchers.IO) {
        getHtml {
            apiClient.getCourseScheduleHtml(dateQuery)
        }
            .mapCatching { html ->
                parseDayCourses(html)
            }
            .onError { msg, e ->
                Timber.e(e, "获取或解析课程信息失败: $msg")
            }
            .log("JwxtCourseRepository")
    }





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