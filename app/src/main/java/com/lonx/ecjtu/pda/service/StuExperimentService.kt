package com.lonx.ecjtu.pda.service

import android.os.Parcelable
import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.jsoup.Jsoup
import java.io.IOException

@Parcelize
data class ExperimentInfo(
    val courseName: String,
    val courseType: String,
    val experimentName: String,
    val experimentType: String,
    val batch: String,
    val time: String,
    val location: String,
    val teacher: String
) : Parcelable


@Parcelize
data class ExperimentData(
    val term: String,
    val termName: String,
    val experiments: List<ExperimentInfo>
): Parcelable

class StuExperimentService(
    private val service: JwxtService,
):BaseService {
    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)
    suspend fun getExperiments(): ServiceResult<List<ExperimentData>> = withContext(Dispatchers.IO) {
        try {
            // Step 1: 获取默认学期（无 term 参数）
            val initialResult = service.getExperimentHtml()
            if (initialResult !is ServiceResult.Success) {
                return@withContext ServiceResult.Error("获取默认实验页面失败")
            }

            val initialDoc = Jsoup.parse(initialResult.data)

            // 提取默认选中的学期
            val defaultOption = initialDoc.selectFirst("select#term > option[selected]")
            val defaultTerm = defaultOption?.attr("value") ?: ""
            val defaultTermName = defaultOption?.text() ?: ""

            // 解析默认学期的实验数据
            val defaultRows = initialDoc.select("table.table_border tr").drop(1)
            val isDefaultNoData = defaultRows.any { it.text().contains("对不起!") }

            val defaultExperiments = if (isDefaultNoData) {
                emptyList()
            } else {
                defaultRows.mapNotNull { row ->
                    val cells = row.select("td")
                    if (cells.size < 9) return@mapNotNull null

                    ExperimentInfo(
                        courseName = cells[1].text(),
                        courseType = cells[2].text(),
                        experimentName = cells[3].text(),
                        experimentType = cells[4].text(),
                        batch = cells[5].text(),
                        time = cells[6].text(),
                        location = cells[7].text(),
                        teacher = cells[8].text()
                    )
                }
            }

            val experimentDataList = mutableListOf(
                ExperimentData(
                    term = defaultTerm,
                    termName = defaultTermName,
                    experiments = defaultExperiments
                )
            )

            // Step 2: 获取其余所有学期选项（跳过默认学期）
            val termOptions = initialDoc.select("select#term > option")
            val otherTerms = termOptions.toList()
                .filter { it.attr("value") != defaultTerm }

            for (option in otherTerms) {
                val termValue = option.attr("value")
                val termName = option.text()

                val termResult = service.getExperimentHtml(term = termValue)
                if (termResult !is ServiceResult.Success) continue

                val termDoc = Jsoup.parse(termResult.data)
                val rows = termDoc.select("table.table_border tr").drop(1)

                val isNoData = rows.any { it.text().contains("对不起!没有当前学期的实验数据") }
                val experiments = if (isNoData) {
                    emptyList()
                } else {
                    rows.mapNotNull { row ->
                        val cells = row.select("td")
                        if (cells.size < 9) return@mapNotNull null

                        ExperimentInfo(
                            courseName = cells[1].text(),
                            courseType = cells[2].text(),
                            experimentName = cells[3].text(),
                            experimentType = cells[4].text(),
                            batch = cells[5].text(),
                            time = cells[6].text(),
                            location = cells[7].text(),
                            teacher = cells[8].text()
                        )
                    }
                }

                experimentDataList.add(
                    ExperimentData(
                        term = termValue,
                        termName = termName,
                        experiments = experiments
                    )
                )
            }

            ServiceResult.Success(experimentDataList)
        } catch (e: Exception) {
            ServiceResult.Error("解析实验数据失败", e)
        }
    }


}