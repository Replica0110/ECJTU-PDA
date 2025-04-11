package com.lonx.ecjtu.pda.data


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 *  课程信息数据类
 *  包含课程的所有结构化信息
 */
@Parcelize
data class StuCourse(
    val courseName: String,
    val teacher: String = "N/A",
    val location: String = "N/A",
    val weeksRaw: String = "N/A",         // 原始周次字符串, e.g., "1-16(单)", "1-16"
    val sectionsRaw: String = "N/A",      // 原始节次字符串, e.g., "1,2", "1,2,3,4"
    val day: WeekDay = WeekDay.MONDAY,             // 课程在哪一天
    val timeSlot: String = "N/A"         // 课程在哪个时间段, e.g., "1-2", "3-4"
    // 将来可能进一步添加解析后的周次列表、开始/结束节次等字段
    // val startWeek: Int,
    // val endWeek: Int,
    // val weekParity: WeekParity, // Enum: ALL, ODD, EVEN
    // val startSection: Int,
    // val endSection: Int
) : Parcelable

@Parcelize
data class StuDayCourses(
    val date: String,
    val courses: List<StuCourse> = emptyList()
) : Parcelable

