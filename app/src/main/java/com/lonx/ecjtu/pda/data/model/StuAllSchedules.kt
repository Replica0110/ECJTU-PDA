package com.lonx.ecjtu.pda.data.model

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
data class TermInfo(
    val value: String, // e.g., "2023.2"
    val name: String   // e.g., "2023-2024 第二学期"
) : Parcelable

@Parcelize
data class TermSchedules(
    val termValue: String, // e.g., "2023.2"
    val termName: String,  // e.g., "2023-2024 第二学期"
    val courses: List<StuCourse> = emptyList() // 包含该学期所有课程的列表
) : Parcelable
@Parcelize
data class StuAllSchedules(
    val allSchedules: List<TermSchedules> = emptyList()
) : Parcelable

