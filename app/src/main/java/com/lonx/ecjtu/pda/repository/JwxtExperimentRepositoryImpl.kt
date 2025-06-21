package com.lonx.ecjtu.pda.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.common.getOrNull
import com.lonx.ecjtu.pda.data.common.map
import com.lonx.ecjtu.pda.data.common.mapCatching
import com.lonx.ecjtu.pda.data.common.onError
import com.lonx.ecjtu.pda.data.model.ExperimentInfo
import com.lonx.ecjtu.pda.data.model.StuAllExperiments
import com.lonx.ecjtu.pda.data.model.TermExperiments
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.repository.ExperimentRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException

class JwxtExperimentRepositoryImpl(
    apiClient: JwxtApiClient,
    authRepository: AuthRepository
) : BaseJwxtRepository(apiClient, authRepository), ExperimentRepository {

    class ParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

    override suspend fun getAllExperiments(): PDAResult<StuAllExperiments> = withContext(Dispatchers.IO) {
        Timber.d("开始获取所有实验信息")

        try {
            val termExperimentsList = mutableListOf<TermExperiments>()

            // 获取默认页面 HTML 并解析
            val initialDocResult = getHtml { apiClient.getExperimentsHtml() }
                .onError { msg, e -> Timber.e(e, "获取默认实验页面失败: $msg") }
                .map { Jsoup.parse(it) }

            val initialDoc = initialDocResult.getOrNull() ?: return@withContext PDAResult.Error("获取默认实验页面失败")

            val defaultOption = initialDoc.selectFirst("select#term > option[selected]")
            val defaultTerm = defaultOption?.attr("value") ?: ""
            val defaultTermName = defaultOption?.text() ?: ""

            val defaultExperiments = parseExperimentRows(initialDoc)

            termExperimentsList.add(
                TermExperiments(
                    term = defaultTerm,
                    termName = defaultTermName,
                    experiments = defaultExperiments
                )
            )

            // 获取其他学期的实验数据
            val otherTerms = initialDoc.select("select#term > option")
                .toList()
                .filter { it.attr("value") != defaultTerm }

            val deferredTerms = otherTerms.map { option ->
                async {
                    val termValue = option.attr("value")
                    val termName = option.text()

                    getHtml { apiClient.getExperimentsHtml(termValue) }
                        .map { Jsoup.parse(it) }
                        .mapCatching { doc ->
                            TermExperiments(
                                term = termValue,
                                termName = termName,
                                experiments = parseExperimentRows(doc)
                            )
                        }
                        .getOrNull()
                }
            }

            termExperimentsList.addAll(deferredTerms.awaitAll().filterNotNull())

            PDAResult.Success(StuAllExperiments(termExperimentsList))
        } catch (e: Exception) {
            Timber.e(e, "实验信息解析异常")
            PDAResult.Error("解析实验数据失败", e)
        }
    }

    private fun parseExperimentRows(doc: Document): List<ExperimentInfo> {
        val rows = doc.select("table.table_border tr").drop(1)
        if (rows.any { it.text().contains("对不起") }) return emptyList()

        return rows.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 9) return@mapNotNull null

            try {
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
            } catch (e: Exception) {
                Timber.w(e, "解析某一行实验数据失败: ${row.text()}")
                null
            }
        }
    }
}
