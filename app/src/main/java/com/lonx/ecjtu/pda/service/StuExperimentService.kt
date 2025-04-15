package com.lonx.ecjtu.pda.service

import android.os.Parcelable
import com.lonx.ecjtu.pda.base.BaseService
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.data.getOrNull
import com.lonx.ecjtu.pda.data.map
import com.lonx.ecjtu.pda.data.mapCatching
import com.lonx.ecjtu.pda.data.onError
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
            val experimentDataList = mutableListOf<ExperimentData>()

            val initialDocResult = service.getExperimentsHtml()
                .onError { msg, e -> Timber.e(e, "获取默认实验页面失败: $msg") }
                .map { Jsoup.parse(it) }

            val initialDoc = initialDocResult.getOrNull() ?: return@withContext ServiceResult.Error("获取默认实验页面失败")

            // 提取默认学期
            val defaultOption = initialDoc.selectFirst("select#term > option[selected]")
            val defaultTerm = defaultOption?.attr("value") ?: ""
            val defaultTermName = defaultOption?.text() ?: ""

            val defaultExperiments = parseExperimentRows(initialDoc)

            experimentDataList.add(
                ExperimentData(
                    term = defaultTerm,
                    termName = defaultTermName,
                    experiments = defaultExperiments
                )
            )

            // 解析其余学期
            val otherTerms = initialDoc.select("select#term > option")
                .toList()
                .filter { it.attr("value") != defaultTerm }

            val deferredTerms = otherTerms.map { option ->
                async {
                    val termValue = option.attr("value")
                    val termName = option.text()

                    service.getExperimentsHtml(term = termValue)
                        .map { Jsoup.parse(it) }
                        .mapCatching { doc ->
                            ExperimentData(
                                term = termValue,
                                termName = termName,
                                experiments = parseExperimentRows(doc)
                            )
                        }
                        .getOrNull()
                }
            }

            val additionalData = deferredTerms.awaitAll().filterNotNull()
            experimentDataList.addAll(additionalData)

            ServiceResult.Success(experimentDataList)
        } catch (e: Exception) {
            ServiceResult.Error("解析实验数据失败", e)
        }
    }
    private fun parseExperimentRows(doc: Document): List<ExperimentInfo> {
        val rows = doc.select("table.table_border tr").drop(1)
        if (rows.any { it.text().contains("对不起") }) return emptyList()

        return rows.mapNotNull { row ->
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



}