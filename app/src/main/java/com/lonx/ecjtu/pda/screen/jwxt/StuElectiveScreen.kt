package com.lonx.ecjtu.pda.screen.jwxt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.ecjtu.pda.service.ElectiveCourseInfo
import com.lonx.ecjtu.pda.service.StudentElectiveCourses
import com.lonx.ecjtu.pda.viewmodel.StuElectiveViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 主页面 Composable
@Composable
fun StuElectiveScreen(
    onBack: () -> Unit,
    viewModel: StuElectiveViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val semesterCourses = uiState.electiveCourses?.allTermsCourses ?: emptyList()

    LaunchedEffect(Unit) {
        if (uiState.electiveCourses == null && !uiState.isLoading) {
            viewModel.loadElectiveCourses()
        }
    }

    LaunchedEffect(semesterCourses.size) {
        if (selectedIndex >= semesterCourses.size && semesterCourses.isNotEmpty()) {
            selectedIndex = semesterCourses.size - 1
        } else if (semesterCourses.isEmpty()) {
            selectedIndex = 0
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            uiState.isLoading -> {
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

            uiState.error != null -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
                        Button(onClick = { viewModel.retryLoadElectiveCourses() }) {
                            Text("重试")
                        }
                    }
                }
            }

            uiState.electiveCourses != null -> {
                item {
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
                        semesterCourses.forEachIndexed { index, semesterCourse ->
                            Tab(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                text = {
                                    Text(
                                        text = semesterCourse.term.value,
                                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                    )
                                },
                                selectedContentColor = MiuixTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val selectedSemester = semesterCourses.getOrNull(selectedIndex)
                if (selectedSemester != null) {
                    if (selectedSemester.courses.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "本学期无课程",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSecondaryVariant
                                )
                            }
                        }
                    } else {
                        items(
                            items = selectedSemester.courses,
                            key = { course -> "${course.term}-${course.teachingClassName}-${course.courseName}" }
                        ) { course ->
                            ElectiveCourseItem(course = course)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ElectiveCourseItem(course: ElectiveCourseInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MiuixTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = course.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(course.courseRequirement, fontSize = 12.sp) },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            HorizontalDivider()

            // 信息区域
            InfoRow("教学班", course.teachingClassName)
            InfoRow("教师", course.instructor)
            InfoRow("时间地点", course.schedule)
            InfoRow("考核方式", course.assessmentMethod)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                DetailText("学分: ${course.credits ?: "N/A"}")
                DetailText("学时: ${course.hours ?: "N/A"}")
                DetailText("类型: ${course.electiveType}")
                DetailText("选课: ${course.selectionType}")
            }

            if (course.subclassName.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                InfoRow("小班名称", course.subclassName)
                course.subclassNumber?.let {
                    InfoRow("小班序号", it)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value.ifEmpty { "N/A" },
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun DetailText(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSecondaryVariant
    )
}