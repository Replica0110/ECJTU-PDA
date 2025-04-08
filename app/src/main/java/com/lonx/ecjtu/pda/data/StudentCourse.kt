package com.lonx.ecjtu.pda.data


import android.os.Parcelable
import kotlinx.parcelize.Parcelize


/**
 *  课程信息数据类
 * */
@Parcelize
data class CourseInfo(
    val courseName: String,
    val courseTime: String = "N/A",
    val courseWeek: String = "N/A",
    val courseLocation: String = "N/A",
    val courseTeacher: String = "N/A"
) : Parcelable
/**
 * 包含日期信息的课程列表数据类
*/
@Parcelize
data class DayCourses(
    val date: String,
    val courses: List<CourseInfo> = emptyList()
) : Parcelable

