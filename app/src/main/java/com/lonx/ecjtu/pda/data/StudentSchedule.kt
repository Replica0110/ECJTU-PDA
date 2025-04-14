package com.lonx.ecjtu.pda.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class WeekDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

@Parcelize
data class StuWeekSchedule(
    val term: String = "", // 可以是 termName 或 termValue
    val day: WeekDay = WeekDay.MONDAY, // 代表这是哪一天的课表
    val dayCourses: List<StuCourse> = emptyList() // 只包含这一天的课程
) : Parcelable {
    companion object {
        fun fromFullSchedule(fullSchedule: FullScheduleResult, targetDay: WeekDay): StuWeekSchedule {
            val dailyCourses = fullSchedule.courses.filter { it.day == targetDay }
            return StuWeekSchedule(
                term = fullSchedule.termName,
                day = targetDay,
                dayCourses = dailyCourses // 使用改进后的 StuCourse
            )
        }
    }
}
@Parcelize
data class TermInfo(
    val value: String, // e.g., "2023.2"
    val name: String   // e.g., "2023-2024 第二学期"
) : Parcelable

@Parcelize
data class FullScheduleResult(
    val termValue: String, // e.g., "2023.2"
    val termName: String,  // e.g., "2023-2024 第二学期"
    val courses: List<StuCourse> = emptyList() // 包含该学期所有课程的列表
) : Parcelable

