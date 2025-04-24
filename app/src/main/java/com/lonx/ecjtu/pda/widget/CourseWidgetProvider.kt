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
import com.lonx.ecjtu.pda.MainActivity
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.model.StuDayCourses
import com.lonx.ecjtu.pda.domain.usecase.GetStuCourseUseCase
import com.lonx.ecjtu.pda.service.CourseRemoteViewsService
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


const val ACTION_MANUAL_REFRESH = "com.lonx.ecjtu.pda.widget.MANUAL_REFRESH"

class CourseWidgetProvider : AppWidgetProvider() {
    private lateinit var getStuCourseUseCase: GetStuCourseUseCase
    private var lastUpdateTime = 0L
    private fun resolveDependencies() {
        if (!::getStuCourseUseCase.isInitialized) {
            try {
                getStuCourseUseCase = GlobalContext.get().get()
            } catch (e: Exception) {
                Timber.tag("WidgetDeps").e(e, "Failed to resolve JwxtService dependency")
            }
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
                val todayResult = getStuCourseUseCase(today)
                val tomorrowResult = getStuCourseUseCase(tomorrow)

                // 提前声明两个变量，用于在主线程中更新 widget
                var todayCourses = StuDayCourses("N/A", emptyList())
                var tomorrowCourses = StuDayCourses("N/A", emptyList())

                when (todayResult) {
                    is ServiceResult.Success -> todayCourses = todayResult.data
                    is ServiceResult.Error -> {
                        Timber.tag("CourseWidgetProvider")
                            .e("获取今天课程失败: ${todayResult.message}")

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
                        updateAppWidget(
                            context,
                            appWidgetManager,
                            appWidgetId,
                            todayCourses,
                            tomorrowCourses,
                            todayResult is ServiceResult.Error,
                            tomorrowResult is ServiceResult.Error)

                    }
                }

            } catch (e: Exception) {
                Timber.tag("CourseWidgetProvider").e("未知异常: ${e.message}")
                e.printStackTrace()
            }
        }

    }
    @Suppress("deprecation")
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        todayCourses: StuDayCourses,
        tomorrowCourses: StuDayCourses,
        todayFetchFailed: Boolean,
        tomorrowFetchFailed: Boolean
    ) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 1000) { // 小于1秒的更新直接跳过
            Timber.tag("小组件更新防抖").e("跳过重复更新")
            return
        }
        lastUpdateTime = now
        resolveDependencies()
        val itemClickIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.lonx.ecjtu.pda.action.VIEW_COURSE_DETAIL"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("widget://item_click_template/$appWidgetId")
        }

        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val itemClickPendingIntentTemplate = PendingIntent.getActivity(
            context,
            appWidgetId,
            itemClickIntent,
            flags
        )
        val randomNumber = System.currentTimeMillis()
        val intentToday = Intent(context, CourseRemoteViewsService::class.java).apply {
            putExtra("dayCourses", Gson().toJson(todayCourses))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("widget://${context.packageName}/$appWidgetId/today/$randomNumber")
        }

        val intentTomorrow = Intent(context, CourseRemoteViewsService::class.java).apply {
            putExtra("dayCourses", Gson().toJson(tomorrowCourses))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("widget://${context.packageName}/$appWidgetId/tomorrow/$randomNumber")
        }
        // 点击刷新按钮
        val refreshIntent = Intent(context, CourseWidgetProvider::class.java).apply {
            action = ACTION_MANUAL_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("widget://${context.packageName}/$appWidgetId/refresh")
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
            setPendingIntentTemplate(R.id.lv_course_today, itemClickPendingIntentTemplate)
            setPendingIntentTemplate(R.id.lv_course_next_day, itemClickPendingIntentTemplate)
            setEmptyView(R.id.lv_course_today, R.id.empty_today)
            setEmptyView(R.id.lv_course_next_day, R.id.empty_next_day)
            setTextViewText(R.id.empty_today, if (todayFetchFailed) "加载今日课程失败" else context.getString(R.string.empty_course))
            setTextViewText(R.id.empty_next_day, if (tomorrowFetchFailed) "加载明日课程失败" else context.getString(R.string.empty_course))
            setTextViewText(R.id.tv_date, date)
            setTextViewText(R.id.tv_week, weekDay)
            setTextViewText(R.id.tv_week_number, weekNumber)
            setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_course_today)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_course_next_day)
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