package com.lonx.ecjtu.pda.service

import android.os.Parcelable
import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.TermInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException

@Parcelize
data class ElectiveCourseInfo(
    val term: String,               // 学期 (e.g., "2023.2")
    val electiveType: String,       // 选课类型 (e.g., "主修")
    val teachingClassName: String,  // 教学班名称
    val courseName: String,         // 课程名称
    val courseRequirement: String,  // 课程要求 (e.g., "必修课")
    val assessmentMethod: String,   // 考核方式 (e.g., "考查")
    val hours: Float?,              // 学时 (Nullable in case parsing fails)
    val credits: Float?,            // 学分 (Nullable in case parsing fails)
    val schedule: String,           // 上课时间 (e.g., "第1-8周 星期一 第5,6节[14-307]")
    val instructor: String,         // 任课教师
    val selectionType: String,      // 选课类型 (Column 10, e.g., "必选")
    val subclassName: String,       // 小班名称
    val subclassNumber: String?      // 小班序号 (Keep as String as it might not always be a number, or handle parsing error)
) : Parcelable


// Groups courses by semester
@Parcelize
data class SemesterCourses(
    val term: TermInfo,
    val courses: List<ElectiveCourseInfo>
) : Parcelable

@Parcelize
data class StudentElectiveCourses(
    val allTermsCourses: List<SemesterCourses>
) : Parcelable


class StuElectiveService(
    private val service: JwxtService
) : BaseService {

    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun getElectiveCourses(): ServiceResult<StudentElectiveCourses> = withContext(Dispatchers.IO) {
        try {
            val initialHtmlResult = service.getElectiveCourseHtml(term = null)
            val initialHtml: String

            when (initialHtmlResult) {
                is ServiceResult.Success -> initialHtml = initialHtmlResult.data
                is ServiceResult.Error -> return@withContext initialHtmlResult
            }

            val initialDocument = Jsoup.parse(initialHtml)

            val semesters = parseSemesters(initialDocument)
            if (semesters.isEmpty()) {
                return@withContext ServiceResult.Success(StudentElectiveCourses(emptyList()))

            }


            val deferredSemesterCourses = semesters.map { semester ->
                async {
                    try {
                        when (val semesterHtmlResult = service.getElectiveCourseHtml(term = semester.value)) {
                            is ServiceResult.Success -> {
                                val semesterDocument = Jsoup.parse(semesterHtmlResult.data)
                                val courses = parseCoursesForTerm(semesterDocument, semester.value)
                                SemesterCourses(semester, courses)
                            }
                            is ServiceResult.Error -> {
                                Timber.e("Failed to fetch courses for semester ${semester.value}: ${semesterHtmlResult.exception}")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e("Error processing semester ${semester.value}: ${e.message}")
                        null
                    }
                }
            }

            val allSemesterCoursesResults = deferredSemesterCourses.awaitAll()

            val successfulSemesterCourses = allSemesterCoursesResults.filterNotNull()

            val studentElectiveCourses = StudentElectiveCourses(successfulSemesterCourses)
            ServiceResult.Success(studentElectiveCourses)

        } catch (e: IOException) {
            ServiceResult.Error("Failed to get elective courses: ${e.message}", e)
        } catch (e: Exception) {
            ServiceResult.Error("An unexpected error occurred: ${e.message}", e)
        }
    }

    private fun parseSemesters(document: Document): List<TermInfo> {
        try {
            val selectElement = document.selectFirst("select#term")
                ?: throw ParseException("Could not find semester select dropdown with id 'term'.")

            return selectElement.select("option").mapNotNull { option ->
                val value = option.attr("value")
                val displayName = option.text()
                if (value.isNotEmpty() && displayName.isNotEmpty()) {
                    TermInfo(value, displayName)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            throw ParseException("Error parsing semester list: ${e.message}", e)
        }
    }

    private fun parseCoursesForTerm(document: Document, expectedTerm: String): List<ElectiveCourseInfo> {
        try {
            val tableBody = document.selectFirst("#dis-exam-info table.table_border tbody")
                ?: return emptyList()

            return tableBody.select("tr").mapNotNull { row ->
                try {

                    val cells = row.select("td")
                    if (cells.size < 13) {
                        Timber.e("Skipping row due to insufficient cells (<13): ${row.html()}")
                        return@mapNotNull null
                    }

                    val term = cells.getOrNull(0)?.text()?.trim() ?: ""

                    ElectiveCourseInfo(
                        term = term,
                        electiveType = cells.getOrNull(1)?.text()?.trim() ?: "",
                        teachingClassName = cells.getOrNull(2)?.text()?.trim() ?: "",
                        courseName = cells.getOrNull(3)?.text()?.trim() ?: "",
                        courseRequirement = cells.getOrNull(4)?.text()?.trim() ?: "",
                        assessmentMethod = cells.getOrNull(5)?.text()?.trim() ?: "",
                        hours = cells.getOrNull(6)?.text()?.trim()?.toFloatOrNull(),
                        credits = cells.getOrNull(7)?.text()?.trim()?.toFloatOrNull(),
                        schedule = cells.getOrNull(8)?.text()?.trim() ?: "",
                        instructor = cells.getOrNull(9)?.text()?.trim() ?: "",
                        selectionType = cells.getOrNull(10)?.text()?.trim() ?: "",
                        subclassName = cells.getOrNull(11)?.text()?.trim() ?: "",
                        subclassNumber = cells.getOrNull(12)?.text()?.trim()
                    )
                } catch (e: Exception) {
                    Timber.e("Failed to parse row: ${row.html()}. Error: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e("Error parsing courses table for term $expectedTerm: ${e.message}")
            return emptyList()
        }
    }
}