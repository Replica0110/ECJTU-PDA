package com.lonx.ecjtu.pda.screen.jwxt

//import androidx.compose.material3.HorizontalDivider
//import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.sharp.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.model.StuAllSchedules
import com.lonx.ecjtu.pda.data.model.StuCourse
import com.lonx.ecjtu.pda.data.model.TermSchedules
import com.lonx.ecjtu.pda.data.model.WeekDay
import com.lonx.ecjtu.pda.state.UiState
import com.lonx.ecjtu.pda.viewmodel.StuScheduleViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StuSchedulesScreen(
    onBack: () -> Unit,
    viewModel: StuScheduleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState !is UiState.Success && uiState !is UiState.Loading && uiState !is UiState.Error) {
            viewModel.load()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
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

            is UiState.Success -> {
                item {
                    // 展示加载成功的数据
                    StuSchedulesContent(stuAllSchedules = state.data)
                }
            }

            is UiState.Error -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Sharp.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error

                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {

            }
        }
    }
}

@Composable
fun StuSchedulesContent(stuAllSchedules: StuAllSchedules) {
    // 按学期对课表进行分组
    val schedules = stuAllSchedules.allSchedules
    val grouped = schedules.groupBy { it.termValue }.toSortedMap(compareByDescending { it })
    val terms = grouped.keys.toList()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    // 学期切换 Tab
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        containerColor = MiuixTheme.colorScheme.surface,
        contentColor = MiuixTheme.colorScheme.primary,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    height = 3.dp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    ) {
        terms.forEachIndexed { index, term ->
            Tab(
                selected = selectedIndex == index,
                onClick = { selectedIndex = index },
                text = {
                    Text(
                        text = term,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                },
                selectedContentColor = MiuixTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 根据所选学期过滤出对应的课表数据
    val schedulesInTerm = grouped[terms[selectedIndex]].orEmpty()
    if (schedulesInTerm.all { it.courses.isEmpty() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "本学期课表为空",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSecondaryVariant
            )
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MiuixTheme.colorScheme.surface)
        ) {
            schedulesInTerm.forEach { ScheduleGrid(it) }
        }
    }
}



val timeSlots = listOf("1-2", "3-4", "5-6", "7-8", "9-10", "11-12")
val weekDays = WeekDay.entries.toList()

const val MIN_CELL_HEIGHT = 80
const val TIME_SLOT_WIDTH = 35

@Composable
fun ScheduleGrid(schedule: TermSchedules) {
    val dayColumnWidth = 60.dp
    val coursesMap = rememberSaveable(schedule.courses) {
        schedule.courses.groupBy { Pair(it.day, it.timeSlot) }
    }
    val uniqueCourseNames = rememberSaveable(schedule.courses) { // 使用 remember 优化性能
        schedule.courses.map { it.courseName }.distinct()
    }
    val courseColorMap = rememberSaveable(uniqueCourseNames) { // 使用 remember 优化性能
        val colors = listOf( // 基础颜色列表，可以放在函数外部或作为常量
            Color(0xff1abc9c),
            Color(0xFFF1C40F),
            Color(0xFF27AE60),
            Color(0xFF9B59B6),
            Color(0xFF34495E),
            Color(0xFF95A5A6),
            Color(0xFFFF6B6B),
            Color(0xFF1BA2FA),
            Color(0xFFFECA57),
            Color(0xFF009293),
            Color(0xF84EAADE),
            Color(0xFFD35400),
        )
        uniqueCourseNames.mapIndexed { index, courseName ->
            val colorIndex = index % colors.size
            courseName to colors[colorIndex].copy(alpha = 0.2f)
        }.toMap()
    }
    val horizontalScrollState = rememberScrollState()

    var selectedCourse by rememberSaveable { mutableStateOf<StuCourse?>(null) }

    if (selectedCourse != null) {
        CourseDetailSheet(course = selectedCourse!!, onDismiss = { selectedCourse = null })
    }

    Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
//        VerticalDivider()
        Column(modifier = Modifier.width(TIME_SLOT_WIDTH.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MiuixTheme.colorScheme.surface) // 背景与表头一致
//                    .border(BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)))
            )
//            HorizontalDivider()

            timeSlots.forEach { timeSlot ->
                val maxCoursesInRow = weekDays.maxOfOrNull { day ->
                    coursesMap[Pair(day, timeSlot)]?.size ?: 0
                } ?: 0
                val rowHeight = (MIN_CELL_HEIGHT * maxCoursesInRow)
                    .coerceAtLeast(MIN_CELL_HEIGHT)
                    .dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
//                        .border(BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                        Text(
                            text = timeSlot.replace("-", "\n|\n"),
                            style = MiuixTheme.textStyles.main.copy(fontSize = 12.sp),
                            textAlign = TextAlign.Center,
                        )

                }
//                HorizontalDivider()
            }
        }

        Box(
            modifier = Modifier
                .weight(1f) // 占据剩余宽度
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            Column {
                // 表头行
                Row(
                    Modifier
                        .height(40.dp)
                        .background(MiuixTheme.colorScheme.surface)
//                        .border(BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)))
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

//                HorizontalDivider()

                // 表格主体
                timeSlots.forEach { timeSlot ->
                    val maxCoursesInRow = weekDays.maxOfOrNull { day ->
                        coursesMap[Pair(day, timeSlot)]?.size ?: 0
                    } ?: 0
                    val rowHeight = (MIN_CELL_HEIGHT * maxCoursesInRow)
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
//                                    .border(BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)))
                                    .padding(2.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Card(
                                    shape = RoundedCornerShape(2.dp),
                                    elevation = CardDefaults.cardElevation(2.dp),
                                    colors = CardDefaults.cardColors(containerColor = MiuixTheme.colorScheme.background),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp),
//                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    coursesInCell.forEach { course ->
                                        val color = courseColorMap[course.courseName] ?: Color.Gray.copy(alpha = 0.4f)
                                        // 将计算好的颜色传递给 CourseItem
                                        CourseItem(course = course, color = color) {
                                            selectedCourse = course
                                        }
                                    }
                                }
                            }
                        }
                    }

//                    HorizontalDivider()
                }
            }
        }
    }
}


@Composable
fun CourseItem(course: StuCourse, color: Color, onClick: () -> Unit) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color)
            .clickable { onClick() } // 加入点击事件
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = course.courseName,
            style = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (course.teacher != "N/A") {
            Text(
                text = course.teacher,
                style = MiuixTheme.textStyles.main.copy(fontSize = 10.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (course.location != "N/A") {
            Text(
                text = "@${course.location}",
                style = MiuixTheme.textStyles.main.copy(fontSize = 10.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = course.weeksRaw,
            style = MiuixTheme.textStyles.main.copy(fontSize = 9.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(course: StuCourse, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MiuixTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .height(400.dp)
                .fillMaxWidth()
                .padding(bottom = 24.dp, top = 8.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                text = course.courseName,
                fontWeight = FontWeight.Bold,
                style = MiuixTheme.textStyles.title4,
            )

            if (course.teacher != "N/A") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 15.dp)) {
                    Icon(Icons.Default.Person, contentDescription = "教师", tint = Color(color = 0xFF007251))
                    Spacer(modifier = Modifier.width(18.dp))
                    Text(
                        text = "教师：${course.teacher}",
                        style = MiuixTheme.textStyles.paragraph.copy(lineHeight = 18.sp)
                    )
                }
            }

            if (course.location != "N/A") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 15.dp)) {
                    Icon(Icons.Default.Place, contentDescription = "地点", tint = Color(color = 0xFF00B800))
                    Spacer(modifier = Modifier.width(18.dp))
                    Text(
                        text = "地点：${course.location}",
                        style = MiuixTheme.textStyles.paragraph.copy(lineHeight = 18.sp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp)) {
                Icon(Icons.Default.DateRange, contentDescription = "周次", tint = Color(color = 0xFF3962FF))
                Spacer(modifier = Modifier.width(18.dp))
                Text(
                    text = "周次：${course.weeksRaw}",
                    style = MiuixTheme.textStyles.paragraph.copy(lineHeight = 18.sp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp)) {
                Icon(painter = painterResource(id = R.drawable.ic_schedule), contentDescription = "节次", tint = Color(color = 0xFFFF7700))
                Spacer(modifier = Modifier.width(18.dp))
                Text(
                    text = "节次：${course.sectionsRaw}",
                    style = MiuixTheme.textStyles.paragraph.copy(lineHeight = 18.sp)
                )
            }
        }
    }
}


fun WeekDay.toChineseShortName(): String {
    return when (this) {
        WeekDay.MONDAY -> "一"
        WeekDay.TUESDAY -> "二"
        WeekDay.WEDNESDAY -> "三"
        WeekDay.THURSDAY -> "四"
        WeekDay.FRIDAY -> "五"
        WeekDay.SATURDAY -> "六"
        WeekDay.SUNDAY -> "日"
    }
}

//fun getColorForCourse(courseName: String): Color {
//    val colors = listOf(
//        Color(0xff1abc9c),
//        Color(0xFFF1C40F),
//        Color(0xFF27AE60),
//        Color(0xFF9B59B6),
//        Color(0xFF34495E),
//        Color(0xFF95A5A6),
//        Color(0xFFFF6B6B),
//        Color(0xFF1BA2FA),
//        Color(0xFFFECA57),
//        Color(0xFF009293),
//    )
//    val index = (courseName.hashCode().absoluteValue) % colors.size
//    return colors[index].copy(alpha = 0.4f)
//}
