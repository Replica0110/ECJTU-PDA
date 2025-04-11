package com.lonx.ecjtu.pda.service

import android.content.Context
import android.content.Intent
import android.os.Bundle // Needed for putting extras neatly
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.StuCourse
import com.lonx.ecjtu.pda.data.StuDayCourses
import timber.log.Timber

const val EXTRA_COURSE_NAME = "com.lonx.ecjtu.pda.widget.EXTRA_COURSE_NAME"
const val EXTRA_COURSE_TIME = "com.lonx.ecjtu.pda.widget.EXTRA_COURSE_TIME"
const val EXTRA_COURSE_LOCATION = "com.lonx.ecjtu.pda.widget.EXTRA_COURSE_LOCATION"

class CourseRemoteViewsFactory(private val context: Context, private val intent: Intent?) :
    RemoteViewsService.RemoteViewsFactory {

    private var courseList: List<StuCourse> = emptyList()

    override fun onCreate() {
        Timber.tag("RemoteViewsFactory").e("Factory created.")
    }

    override fun onDataSetChanged() {
        val json = intent?.getStringExtra("dayCourses")
        if (json != null) {
            try {
                val type = object : TypeToken<StuDayCourses>() {}.type
                val deserializedDayCourses: StuDayCourses = Gson().fromJson(json, type)

                courseList = deserializedDayCourses.courses


            } catch (e: Exception) {
                courseList = emptyList()
            }
        } else {
            courseList = emptyList()
        }
    }

    override fun onDestroy() {
        Timber.tag("CourseFactory").d("onDestroy")
        courseList = emptyList()
    }

    override fun getCount(): Int {
        val count = courseList.size
        Timber.tag("CourseFactory").v("getCount: $count")
        return count
    }

    override fun getViewAt(position: Int): RemoteViews? {
        Timber.tag("CourseFactory").v("getViewAt: position $position")

        if (position < 0 || position >= courseList.size) {
            return null
        }

        val course = courseList[position]

        val itemView = RemoteViews(context.packageName, R.layout.widget_course_item).apply {
            setTextViewText(R.id.tv_course_name, course.courseName)
            setTextViewText(R.id.tv_course_time, course.sectionsRaw)
            setTextViewText(R.id.tv_course_location, course.location)
        }

        val fillInIntent = Intent().apply {
            val extras = Bundle()
            extras.putString(EXTRA_COURSE_NAME, course.courseName)
            extras.putString(EXTRA_COURSE_TIME, course.sectionsRaw)
            extras.putString(EXTRA_COURSE_LOCATION, course.location)
            putExtras(extras)
        }

        itemView.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
        // -----------------------------------------------------

        return itemView
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }


    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return false
    }
}