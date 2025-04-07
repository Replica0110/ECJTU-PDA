package com.lonx.ecjtu.pda.data

import com.google.gson.annotations.SerializedName

/**
 * Represents credit requirements (required, completed, owed).
 * 用于表示学分要求（应完成、已完成、欠学分）。
 */
data class RequirementCredits(
    @SerializedName("required")
    val required: Double = 0.0, // 应完成学分 (Required credits)

    @SerializedName("completed")
    val completed: Double = 0.0, // 已完成学分 (Completed credits)

    @SerializedName("owed")
    val owed: Double = 0.0      // 欠学分 (Owed credits)
)

/**
 * Holds the summary data parsed from the top table on the scores page.
 * 包含从成绩页面顶部表格解析出的摘要数据。
 */
data class ScoreSummary(
    @SerializedName("academic_warning_required")
    val academicWarningRequired: Double? = null, // 学业预警情况 - 应完成 (Academic warning - Required) - Nullable as it might not always be present or numeric

    @SerializedName("academic_warning_completed")
    val academicWarningCompleted: Double? = null, // 学业预警情况 - 已完成 (Academic warning - Completed) - Nullable

    @SerializedName("gpa_required")
    val gpaRequired: Double? = null,          // 学位平均学分绩点要求 - 应获得 (Degree GPA requirement - Required) - Nullable

    @SerializedName("gpa_achieved")
    val gpaAchieved: Double? = null,          // 学位平均学分绩点要求 - 已获得 (Degree GPA requirement - Achieved) - Nullable

    @SerializedName("public_elective")
    val publicElective: RequirementCredits = RequirementCredits(), // 公共任选课学分 (Public elective credits)

    @SerializedName("mgmt_law_elective")
    val mgmtLawElective: RequirementCredits = RequirementCredits(), // 经管法学类公共任选课学分 (Management/Law elective credits)

    @SerializedName("humanities_art_elective")
    val humanitiesArtElective: RequirementCredits = RequirementCredits(), // 人文艺术类公共任选课学分 (Humanities/Art elective credits)

    @SerializedName("science_tech_elective")
    val scienceTechElective: RequirementCredits = RequirementCredits(), // 科学技术类公共任选课学分 (Science/Technology elective credits)

    @SerializedName("health_elective")
    val healthElective: RequirementCredits = RequirementCredits(), // 身心健康类公共任选课学分 (Health elective credits)

    @SerializedName("discipline_elective")
    val disciplineElective: RequirementCredits = RequirementCredits(), // 学科任选课学分 (Discipline elective credits)

    @SerializedName("major_elective")
    val majorElective: RequirementCredits = RequirementCredits() // 专业任选课学分 (Major elective credits)
)

/**
 * Represents a single course score entry from the detailed list.
 * 代表详细列表中的单个课程成绩条目。
 */
data class CourseScore(
    @SerializedName("term")
    val term: String = "",                 // 学期 (e.g., "2024.1")

    @SerializedName("course_id")
    val courseId: String? = null,          // 课程代码 (e.g., "1500190180") - Nullable if parsing fails

    @SerializedName("course_name")
    val courseName: String = "",           // 课程名称 (e.g., "专业创新创业实践")

    @SerializedName("requirement_type")
    val requirementType: String = "",      // 课程要求 (e.g., "必修课")

    @SerializedName("assessment_method")
    val assessmentMethod: String = "",     // 考核方式 (e.g., "考查")

    @SerializedName("credits")
    val credits: Double = 0.0,             // 课程学分 (e.g., 2.0)

    @SerializedName("score")
    val score: String? = null,             // 考试成绩 (e.g., "及格", "95", null if empty) - String type to accommodate non-numeric grades

    @SerializedName("retake_score")
    val retakeScore: String? = null,       // 重考成绩 (Nullable)

    @SerializedName("resit_score")
    val resitScore: String? = null         // 重修成绩 (Nullable)
)

/**
 * Container for the overall score data, including summary and detailed course scores.
 * 包含整体成绩数据的容器，包括摘要信息和详细课程成绩列表。
 */
data class StudentScoreData(
    @SerializedName("summary")
    val summary: ScoreSummary = ScoreSummary(), // The summary section parsed from the top table

    @SerializedName("detailed_scores")
    val detailedScores: List<CourseScore> = emptyList() // List of individual course scores
)