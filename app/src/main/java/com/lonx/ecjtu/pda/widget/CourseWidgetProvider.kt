package com.lonx.ecjtu.pda.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.google.gson.Gson
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.CourseData
import com.lonx.ecjtu.pda.data.ServiceResult
import com.lonx.ecjtu.pda.service.CourseRemoteViewsService
import com.lonx.ecjtu.pda.service.JwxtService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


const val ACTION_MANUAL_REFRESH = "com.lonx.ecjtu.widget.MANUAL_REFRESH"

class CourseWidgetProvider : AppWidgetProvider() {
    private lateinit var service: JwxtService
    private var lastUpdateTime = 0L
    private fun resolveDependencies() {
        if (!::service.isInitialized) {
            service = GlobalContext.get().get()
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        resolveDependencies()
        intent.action?.let { Timber.tag("intent.action").e(it) }
        if (shouldUpdateWidget(intent.action)) {
            updateWidgets(context)
        }
    }

    private fun shouldUpdateWidget(action: String?): Boolean {
        resolveDependencies()
        return action in listOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            ACTION_MANUAL_REFRESH,
            "android.appwidget.action.APPWIDGET_UPDATE"
        )
    }

    private fun updateWidgets(context: Context) {
        resolveDependencies()

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, CourseWidgetProvider::class.java)
        )
        onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        resolveDependencies()
        Timber.tag("onEnabled").e("onEnabled")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Timber.tag("onDisabled").e("onDisabled")
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        resolveDependencies()
        if (appWidgetIds.isEmpty()) {
            Timber.tag("appWidgetIds").e("appWidgetIds is empty")
            return
        }
        val today = getFormatDate()
        val tomorrow = getFormatDate(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todayResult = service.getCourseSchedule(today)
                val tomorrowResult = service.getCourseSchedule(tomorrow)

                // 提前声明两个变量，用于在主线程中更新 widget
                var todayCourses: CourseData.DayCourses = CourseData.DayCourses("N/A", emptyList())
                var tomorrowCourses: CourseData.DayCourses = CourseData.DayCourses("N/A", emptyList())

                when (todayResult) {
                    is ServiceResult.Success -> todayCourses = todayResult.data
                    is ServiceResult.Error -> {
                        Timber.tag("CourseWidgetProvider")
                            .e("获取今天课程失败: ${todayResult.message}")
                        // 你也可以选择展示错误信息，或保留空课程
                    }
                }

                when (tomorrowResult) {
                    is ServiceResult.Success -> tomorrowCourses = tomorrowResult.data
                    is ServiceResult.Error -> {
                        Timber.tag("CourseWidgetProvider")
                            .e("获取明天课程失败: ${tomorrowResult.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    appWidgetIds.forEach { appWidgetId ->
                        updateAppWidget(context, appWidgetManager, appWidgetId, todayCourses, tomorrowCourses)
                    }
                }

            } catch (e: Exception) {
                Timber.tag("CourseWidgetProvider").e("未知异常: ${e.message}")
                e.printStackTrace()
            }
        }

    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        todayCourses: CourseData.DayCourses,
        tomorrowCourses: CourseData.DayCourses
    ) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 1000) { // 小于1秒的更新直接跳过
            Timber.tag("小组件更新防抖").e("跳过重复更新")
            return
        }
        lastUpdateTime = now
        resolveDependencies()
        val randomNumber = System.currentTimeMillis() // Use a unique value for each update
        val intentToday = Intent(context, CourseRemoteViewsService::class.java).apply {
            putExtra("dayCourses", Gson().toJson(todayCourses))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("random", randomNumber) // Adding random number to the intent
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME)) // Make the intent unique
        }

        val intentTomorrow = Intent(context, CourseRemoteViewsService::class.java).apply {
            putExtra("dayCourses", Gson().toJson(tomorrowCourses))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("random", randomNumber) // Adding random number to the intent
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME)) // Make the intent unique
        }
        // 点击刷新按钮
        val refreshIntent = Intent(context, CourseWidgetProvider::class.java).apply {
            action = ACTION_MANUAL_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME)) // 唯一化 Intent
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId, // 用 widgetId 作为 requestCode 保证唯一性
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (date, weekDay, weekNumber) = getToday(todayCourses.date)
        val views = RemoteViews(context.packageName, R.layout.widget_course).apply {
            setRemoteAdapter(R.id.lv_course_today, intentToday)
            setRemoteAdapter(R.id.lv_course_next_day, intentTomorrow)
            setEmptyView(R.id.lv_course_today, R.id.empty_today)
            setEmptyView(R.id.lv_course_next_day, R.id.empty_next_day)
            setTextViewText(R.id.tv_date, date)
            setTextViewText(R.id.tv_week, weekDay)
            setTextViewText(R.id.tv_week_number, weekNumber)
            setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }


    private fun getToday(time:String): Triple<String, String, String> {
        val pattern = """(\d{4}-\d{2}-\d{2})\s(星期.+)（(第\d+周)）""".toRegex()

        val matchResult = pattern.find(time)
        if (matchResult != null) {
            val (date, weekDay, weekNumber) = matchResult.destructured
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("M.d", Locale.getDefault())
            val formatDate = inputFormat.parse(date)?: Date()
            val formattedDate = outputFormat.format(formatDate)
            return Triple(formattedDate, weekDay, weekNumber)
        } else {
            return Triple("11.45", "星期八", "第14周")
        }
    }

    private fun getFormatDate(tomorrow: Boolean = false): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        if (tomorrow) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dateFormat.format(calendar.time)
    }
}