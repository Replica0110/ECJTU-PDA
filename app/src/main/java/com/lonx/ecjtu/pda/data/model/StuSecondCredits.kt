package com.lonx.ecjtu.pda.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the extracurricular credits earned in a specific academic year.
 * 代表在特定学年获得的素质拓展学分。
 *
 * @property academicYear The academic year string (e.g., "2023"). 学年字符串。
 * @property creditsByCategory A map where the key is the credit category name (e.g., "文体艺术")
 *                             and the value is the credits earned (Double).
 *                             Map结构，键是学分类别名（如“文体艺术”），值是获得的分数（Double）。
 */
data class YearlySecondCredits(
    @SerializedName("academic_year")
    val academicYear: String = "",

    @SerializedName("credits_by_category")
    val creditsByCategory: Map<String, Double> = emptyMap()
)

/**
 * Container for the overall extracurricular credit data.
 * 包含整体素质拓展学分数据的容器。
 *
 * @property totalCreditsByCategory A map representing the total credits for each category across all years.
 *                                   代表所有学年各类别总学分的Map。
 * @property yearlyCredits A list containing the credit breakdown for each academic year.
 *                          包含各学年学分明细的列表。
 */
data class StuSecondCredits(
    @SerializedName("total_credits_by_category")
    val totalCreditsByCategory: Map<String, Double> = emptyMap(),

    @SerializedName("yearly_credits")
    val yearlyCredits: List<YearlySecondCredits> = emptyList()
)

