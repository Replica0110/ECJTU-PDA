package com.lonx.ecjtu.pda.service

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.CourseData
import timber.log.Timber

class CourseRemoteViewsFactory(private val context: Context, private val intent: Intent) : RemoteViewsService.RemoteViewsFactory {

    private var courseList: ArrayList<CourseData.CourseInfo> = ArrayList()

    override fun onCreate() {
        Timber.tag("RemoteViewsFactory").e("Factory created.")
    }

    override fun onDataSetChanged() {
        loadDataInBackground()
    }

    override fun onDestroy() {
        courseList.clear()
    }
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()
    override fun getCount(): Int {
        return courseList.size.takeIf { it != 1 || courseList[0].courseName != "今天没有课程" } ?: 0
    }

    override fun getViewAt(position: Int): RemoteViews? {
        val course = courseList[position]

        // 如果是“今天无课程”的占位项，返回 null，让 setEmptyView 生效
        return if (course.courseName == "今天没有课程") {
            null
        } else {
            RemoteViews(context.packageName, R.layout.widget_course_item).apply {
                setTextViewText(R.id.tv_course_name, course.courseName)
                setTextViewText(R.id.tv_course_time, course.courseTime)
                setTextViewText(R.id.tv_course_location, course.courseLocation)
            }
        }
    }

    override fun hasStableIds(): Boolean = true

    private fun loadDataInBackground() {
        val dayCourses = intent.getStringExtra("dayCourses")
        val type = object : TypeToken<CourseData.DayCourses>() {}.type
        val deserializedDayCourses: CourseData.DayCourses = Gson().fromJson(dayCourses, type)

        courseList.clear()

        if (deserializedDayCourses.courses.isEmpty()) {
            // 添加一个“无课程”占位
            courseList.add(
                CourseData.CourseInfo(
                    courseName = "今天没有课程",
                    courseTime = "",
                    courseWeek = "",
                    courseLocation = "",
                    courseTeacher = ""
                )
            )
        } else {
            courseList.addAll(deserializedDayCourses.courses)
        }
    }
}
