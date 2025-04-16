package com.lonx.ecjtu.pda.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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


@Parcelize
data class SemesterCourses(
    val term: TermInfo,
    val courses: List<ElectiveCourseInfo>
) : Parcelable

@Parcelize
data class StuAllElectiveCourses(
    val allTermsCourses: List<SemesterCourses>
) : Parcelable