package com.lonx.ecjtu.pda.screen.jwxt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.ecjtu.pda.data.FullScheduleResult
import com.lonx.ecjtu.pda.data.StuCourse
import com.lonx.ecjtu.pda.data.WeekDay
import com.lonx.ecjtu.pda.viewmodel.StuScheduleViewModel
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StuSchedulesScreen(
    onBack: () -> Unit,
    viewModel:StuScheduleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState.SchedulesData== null && !uiState.isLoading){
            viewModel.loadSchedules()
        }
    }
    LazyColumn(modifier = Modifier
        .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            uiState.isLoading -> {
                item {
                    Box(modifier = Modifier
                        .fillParentMaxSize()
                        .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "如果加载时间较长，可能登录已过期，应用正在重新登录",
                            color = Color.Gray,
                            style = MiuixTheme.textStyles.subtitle,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 30.dp)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            uiState.SchedulesData != null -> {
                Timber.e("schedules: ${uiState.SchedulesData}")
                item{
                    StuSchedulesContent(
                        schedules = uiState.SchedulesData!!
                    )
                }
            }

            uiState.error != null -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "加载错误",
                            style = MiuixTheme.textStyles.title1,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "无法加载课表信息",
                            textAlign = TextAlign.Center,
                            style = MiuixTheme.textStyles.paragraph,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retryLoadSchedules() }) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun StuSchedulesContent(
    schedules: List<FullScheduleResult>
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(schedules) {
        if (selectedTabIndex >= schedules.size && schedules.isNotEmpty()) {
            selectedTabIndex = schedules.size - 1
        } else if (schedules.isEmpty()) {
            selectedTabIndex = 0
        }
    }

        Column(
                modifier = Modifier.fillMaxSize(),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = MiuixTheme.colorScheme.surface,
                contentColor = MiuixTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            height = 3.dp,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                schedules.forEachIndexed { index, schedule ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = schedule.termName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MiuixTheme.textStyles.title1.copy(fontSize = 14.sp)
                            )
                        },
                        selectedContentColor = MiuixTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            key(selectedTabIndex) {
                if (schedules.isNotEmpty() && selectedTabIndex < schedules.size) {
                    ScheduleGrid(schedule = schedules[selectedTabIndex])
                } else {
                    Box(Modifier
                        .fillMaxSize()
                        .padding(16.dp), contentAlignment = Alignment.Center){
                        Text("无法显示课表")
                    }
                }
            }
        }

}


val timeSlots = listOf("1-2", "3-4", "5-6", "7-8", "9-10", "11-12")
val weekDays = WeekDay.entries.toList()

const val MIN_CELL_HEIGHT = 80
const val TIME_SLOT_WIDTH = 35

@Composable
fun ScheduleGrid(schedule: FullScheduleResult) {
    val dayColumnWidth = 60.dp
    val coursesMap = remember(schedule.courses) {
        schedule.courses.groupBy { Pair(it.day, it.timeSlot) }
    }
    val horizontalScrollState = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.width(TIME_SLOT_WIDTH.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MiuixTheme.colorScheme.surface) // 背景与表头一致
                    .border(BorderStroke(0.5.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)))
            )
            Divider()

            timeSlots.forEach { timeSlot ->
                val maxCoursesInRow = weekDays.maxOfOrNull { day ->
                    coursesMap[Pair(day, timeSlot)]?.size ?: 0
                } ?: 0
                val rowHeight = (MIN_CELL_HEIGHT + (maxCoursesInRow - 1).coerceAtLeast(0) * 70)
                    .coerceAtLeast(MIN_CELL_HEIGHT)
                    .dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .border(
                            BorderStroke(
                                0.5.dp,
                                MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                        Text(
                            text = timeSlot.replace("-", "\n|\n"),
                            style = MiuixTheme.textStyles.main.copy(fontSize = 12.sp),
                            textAlign = TextAlign.Center,
                        )

                }
                Divider()
            }
        }

        Box(
            modifier = Modifier
                .weight(1f) // 占据剩余宽度
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            Column {
                Row(
                    Modifier
                        .height(40.dp)
                        .background(MiuixTheme.colorScheme.surface)
                        .border(
                            BorderStroke(
                                0.5.dp,
                                MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                ) {
                    weekDays.forEach { day ->
                        Box(
                            modifier = Modifier
                                .width(dayColumnWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.toChineseShortName(),
                                style = MiuixTheme.textStyles.main,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                HorizontalDivider()

                timeSlots.forEach { timeSlot ->
                    val maxCoursesInRow = weekDays.maxOfOrNull { day ->
                        coursesMap[Pair(day, timeSlot)]?.size ?: 0
                    } ?: 0
                    val rowHeight = (MIN_CELL_HEIGHT + (maxCoursesInRow - 1).coerceAtLeast(0) * 70)
                        .coerceAtLeast(MIN_CELL_HEIGHT)
                        .dp

                    Row(
                        Modifier
                            .height(rowHeight)
                    ) {
                        weekDays.forEach { day ->
                            val coursesInCell = coursesMap[Pair(day, timeSlot)] ?: emptyList()
                            Box(
                                modifier = Modifier
                                    .width(dayColumnWidth)
                                    .fillMaxHeight()
                                    .border(
                                        BorderStroke(
                                            0.5.dp,
                                            MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    if (coursesInCell.isNotEmpty()) {
                                        coursesInCell.forEach { course ->
                                            CourseItem(course = course)
                                        }
                                    } else {
                                        // Empty cell
                                    }
                                }
                            }
                        }
                    }
                    Divider() // 行分隔线
                }
            }
        }
    }
}


@Composable
fun CourseItem(course: StuCourse) {
    val backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha=0.08f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = course.courseName,
            style = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            maxLines = 2,
        )
        if (course.teacher != "N/A") {
            Text(
                text = course.teacher,
                style = MiuixTheme.textStyles.main.copy(fontSize = 10.sp),
                maxLines = 1,
            )
        }
        if (course.location != "N/A") {
            Text(
                text = "@${course.location}",
                style = MiuixTheme.textStyles.main.copy(fontSize = 10.sp),
                maxLines = 1,
            )
        }
        Text(
            text = "${course.weeksRaw} [${course.sectionsRaw}]",
            style = MiuixTheme.textStyles.main.copy(fontSize = 9.sp),
            maxLines = 1,
        )
    }
}


fun WeekDay.toChineseShortName(): String {
    return when (this) {
        WeekDay.MONDAY -> "周一"
        WeekDay.TUESDAY -> "周二"
        WeekDay.WEDNESDAY -> "周三"
        WeekDay.THURSDAY -> "周四"
        WeekDay.FRIDAY -> "周五"
        WeekDay.SATURDAY -> "周六"
        WeekDay.SUNDAY -> "周日"
    }
}

