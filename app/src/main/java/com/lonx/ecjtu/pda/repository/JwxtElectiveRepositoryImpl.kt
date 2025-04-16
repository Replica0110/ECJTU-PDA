package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.common.getOrNull
import com.lonx.ecjtu.pda.data.common.map
import com.lonx.ecjtu.pda.data.common.mapCatching
import com.lonx.ecjtu.pda.data.common.onError
import com.lonx.ecjtu.pda.data.model.ElectiveCourseInfo
import com.lonx.ecjtu.pda.data.model.SemesterCourses
import com.lonx.ecjtu.pda.data.model.StuAllElectiveCourses
import com.lonx.ecjtu.pda.data.model.TermInfo
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.repository.ElectiveRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException

class JwxtElectiveRepositoryImpl(
    apiClient: JwxtApiClient,
    authRepository: AuthRepository
) : BaseJwxtRepository(apiClient, authRepository), ElectiveRepository{
    override suspend fun getStudentElectiveCourse(): ServiceResult<StuAllElectiveCourses> = withContext(Dispatchers.IO) {
        return@withContext try {
            fetchHtmlWithRelogin { apiClient.getElectiveCourseHtml()}
                .onError { msg, e -> Timber.e(e, "获取初始选课页失败: $msg") }
                .map { Jsoup.parse(it) }

                .mapCatching { parseSemesters(it) }
                .mapCatching { semesters ->
                    if (semesters.isEmpty()) {
                        return@withContext ServiceResult.Success(StuAllElectiveCourses(emptyList()))
                    }
                    val deferredSemesterCourses = semesters.map { semester ->
                        async {
                            apiClient.getElectiveCourseHtml(term = semester.value)
                                .onError { msg, e ->
                                    Timber.e(e, "获取学期 ${semester.value} 的选课数据失败: $msg")
                                }
                                .map { Jsoup.parse(it) }
                                .mapCatching { parseCoursesForTerm(it, semester.value) }
                                .getOrNull()
                                ?.let { SemesterCourses(semester, it) }
                        }
                    }

                    val semesterCourses = deferredSemesterCourses.awaitAll().filterNotNull()
                    StuAllElectiveCourses(semesterCourses)
                }
        } catch (e: IOException) {
            ServiceResult.Error("网络错误: ${e.message}", e)
        } catch (e: Exception) {
            ServiceResult.Error("未知错误: ${e.message}", e)
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
            throw ParseException(
                "Error parsing semester list: ${e.message}",
                e
            )
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
