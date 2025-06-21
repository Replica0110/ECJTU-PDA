package com.lonx.ecjtu.pda.screen.jwxt

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.lonx.ecjtu.pda.data.model.ElectiveCourseInfo
import com.lonx.ecjtu.pda.data.model.SemesterCourses
import com.lonx.ecjtu.pda.state.UiState
import com.lonx.ecjtu.pda.ui.component.GenericTabs
import com.lonx.ecjtu.pda.viewmodel.StuElectiveViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.TabRow
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

    val semesterCourses = (uiState as? UiState.Success)?.data?.allTermsCourses

    LaunchedEffect(semesterCourses) {
        selectedIndex = if (semesterCourses != null) {
            when {
                selectedIndex >= semesterCourses.size && semesterCourses.isNotEmpty() -> semesterCourses.size - 1
                semesterCourses.isEmpty() -> 0
                selectedIndex < 0 -> 0
                else -> selectedIndex
            }
        } else {
            0
        }
    }
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

            is UiState.Success -> {
                val currentSemesterCourses = state.data.allTermsCourses

                if (currentSemesterCourses.isEmpty()) {
                    // 处理完全没有学期数据的情况
                    item { NoDataView(message = "暂无任何学期数据") }
                } else {
                    item {
                        top.yukonga.miuix.kmp.basic.Card {
                            GenericTabs(
                                items = semesterCourses,
                                selectedIndex = selectedIndex,
                                onTabSelected = { selectedIndex = it },
                                getLabel = { it.term.value }
                            )
                        }
                    }

                    val safeSelectedIndex = selectedIndex.coerceIn(0, currentSemesterCourses.size - 1)
                    val selectedSemester = currentSemesterCourses.getOrNull(safeSelectedIndex)

                    if (selectedSemester != null) {
                        if (selectedSemester.courses.isEmpty()) {
                            // 处理当前学期没有课程的情况
                            item { NoDataView(message = "本学期无课程") }
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

            else -> {}
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
@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth() // 填充宽度
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "加载错误",
            style = MiuixTheme.textStyles.title1, // 替换为你的样式
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MiuixTheme.textStyles.paragraph, // 替换为你的样式
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

/**
 * 可组合项：显示无数据状态
 */
@Composable
fun NoDataView(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2, // 替换为你的样式
            color = MiuixTheme.colorScheme.onSecondaryVariant // 替换为你的颜色
        )
    }
}