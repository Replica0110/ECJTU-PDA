package com.lonx.ecjtu.pda.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * 用于表示学分要求（应完成、已完成、欠学分）。
 */
@Parcelize
data class RequirementCredits(
    @SerializedName("required")
    val required: Double = 0.0,

    @SerializedName("completed")
    val completed: Double = 0.0,

    @SerializedName("owed")
    val owed: Double = 0.0
) : Parcelable

/**
 * 包含从成绩页面顶部表格解析出的摘要数据。
 */
@Parcelize
data class ScoreSummary(
    @SerializedName("academic_warning_required")
    val academicWarningRequired: Double? = null,

    @SerializedName("academic_warning_completed")
    val academicWarningCompleted: Double? = null,

    @SerializedName("gpa_required")
    val gpaRequired: Double? = null,

    @SerializedName("gpa_achieved")
    val gpaAchieved: Double? = null,

    @SerializedName("public_elective")
    val publicElective: RequirementCredits = RequirementCredits(),

    @SerializedName("mgmt_law_elective")
    val mgmtLawElective: RequirementCredits = RequirementCredits(),

    @SerializedName("humanities_art_elective")
    val humanitiesArtElective: RequirementCredits = RequirementCredits(),

    @SerializedName("science_tech_elective")
    val scienceTechElective: RequirementCredits = RequirementCredits(),

    @SerializedName("health_elective")
    val healthElective: RequirementCredits = RequirementCredits(),

    @SerializedName("discipline_elective")
    val disciplineElective: RequirementCredits = RequirementCredits(),

    @SerializedName("major_elective")
    val majorElective: RequirementCredits = RequirementCredits()
) : Parcelable

/**
 * 代表详细列表中的单个课程成绩条目。
 */
@Parcelize
data class CourseScore(
    @SerializedName("term")
    val term: String = "",

    @SerializedName("course_id")
    val courseId: String? = null,

    @SerializedName("course_name")
    val courseName: String = "",

    @SerializedName("requirement_type")
    val requirementType: String = "",

    @SerializedName("assessment_method")
    val assessmentMethod: String = "",

    @SerializedName("credits")
    val credits: Double = 0.0,

    @SerializedName("score")
    val score: String? = null,

    @SerializedName("retake_score")
    val retakeScore: String? = null,

    @SerializedName("resit_score")
    val resitScore: String? = null
) : Parcelable


/**
 * Container for the overall score data, including summary and detailed course scores.
 * 包含整体成绩数据的容器，包括摘要信息和详细课程成绩列表。
 */
@Parcelize
data class StuAllScores(
    @SerializedName("summary")
    val summary: ScoreSummary = ScoreSummary(),

    @SerializedName("detailed_scores")
    val detailedScores: List<CourseScore> = emptyList()
) : Parcelable
