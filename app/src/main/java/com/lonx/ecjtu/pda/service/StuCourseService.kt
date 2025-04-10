package com.lonx.ecjtu.pda.service

import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.CourseInfo
import com.lonx.ecjtu.pda.data.DayCourses
import com.lonx.ecjtu.pda.data.ServiceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException

class StuCourseService(
    private val service: JwxtService
) : BaseService {

    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun getCourseSchedule(dateQuery: String? = null): ServiceResult<DayCourses> = withContext(Dispatchers.IO) {
        when (val htmlResult = service.getCourseScheduleHtml(dateQuery)) {
            is ServiceResult.Success -> {
                try {
                    val html = htmlResult.data
                    val document = Jsoup.parse(html)

                    val extractedDate = document.selectFirst("div.center")?.text()?.trim() ?: "日期未知"
                    val courseListElement = document.selectFirst("div.calendar ul.rl_info")
                        ?: return@withContext ServiceResult.Success(DayCourses(extractedDate, emptyList()))

                    if (courseListElement.selectFirst("li > p > img") != null) {
                        return@withContext ServiceResult.Success(DayCourses(extractedDate, emptyList()))
                    }

                    val courses = mutableListOf<CourseInfo>()
                    val listItems = courseListElement.select("li")

                    for (item in listItems) {
                        val pElement = item.selectFirst("p") ?: continue
                        val textParts = pElement.childNodes()
                            .filterIsInstance<org.jsoup.nodes.TextNode>()
                            .mapNotNull { it.text().trim().takeIf { txt -> txt.isNotEmpty() } }

                        val courseName = textParts.getOrNull(0) ?: continue

                        val timeInfo = textParts.getOrNull(1)?.substringAfter("时间：")?.split(" ") ?: listOf()
                        val week = timeInfo.getOrNull(0) ?: "N/A"
                        val time = timeInfo.getOrNull(1) ?: "N/A"
                        val location = textParts.getOrNull(2)?.substringAfter("地点：") ?: "N/A"
                        val teacher = textParts.getOrNull(3)?.substringAfter("教师：") ?: "N/A"

                        courses.add(
                            CourseInfo(
                                courseName = courseName,
                                courseTime = "节次：$time",
                                courseWeek = "上课周：$week",
                                courseLocation = "地点：$location",
                                courseTeacher = "教师：$teacher"
                            )
                        )
                    }

                    return@withContext ServiceResult.Success(DayCourses(extractedDate, courses))
                } catch (e: Exception) {
                    Timber.e(e, "解析课程表 HTML 时出错")
                    return@withContext ServiceResult.Error("解析失败: ${e.message}", e)
                }
            }

            is ServiceResult.Error -> return@withContext htmlResult
        }
    }
}
