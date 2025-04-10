package com.lonx.ecjtu.pda.screen.jwxt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lonx.ecjtu.pda.data.CourseScore
import com.lonx.ecjtu.pda.data.RequirementCredits
import com.lonx.ecjtu.pda.data.ScoreSummary
import com.lonx.ecjtu.pda.data.StudentScoresData
import com.lonx.ecjtu.pda.viewmodel.StuScoreViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StuScoreScreen(
    onBack: () -> Unit,
//    scrollBehavior: UpdatableScrollBehavior,
    viewModel: StuScoreViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState.studentScoreData == null && !uiState.isLoading) {
            viewModel.loadScores()
        }
    }

//    val nestedScrollConnection = rememberAppBarNestedScrollConnection(scrollBehavior)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
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
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "错误",
                            style = MiuixTheme.textStyles.paragraph,
                            color = MiuixTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "无法加载成绩信息",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retryLoadScores() }) {
                            Text("重试")
                        }
                    }
                }
            }

            uiState.studentScoreData != null -> {
                scoreContent(data = uiState.studentScoreData!!)
            }
        }
    }
}

fun LazyListScope.scoreContent(data: StudentScoresData) {
    val groupedScores = data.detailedScores
        .groupBy { it.term }
        .toSortedMap(compareByDescending { term -> term })

    // 成绩摘要部分
    item {
        Column {
            SmallTitle("绩点总览")
            ScoreSummaryCard(summary = data.summary)

        }
    }

    if (groupedScores.isEmpty()) {
        item {
            SmallTitle("详细成绩")
            Text(
                "暂无详细成绩记录",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        item {
            SmallTitle("详细成绩")
            ScoreDetailSection(scores = data.detailedScores)
        }
    }
}


@Composable
fun ScoreSummaryCard(summary: ScoreSummary) {
    val expanded = remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // GPA 绩点部分
            Text("学位绩点", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("要求: ${summary.gpaRequired ?: "N/A"}")
                Text("已获得: ${summary.gpaAchieved ?: "N/A"}")
            }

            summary.academicWarningRequired?.let { req ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    Text("学业预警", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("要求: $req")
                        Text("已获得: ${summary.academicWarningCompleted ?: "N/A"}")
                    }
                }
            }

            //  毕业要求
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded.value = !expanded.value },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("毕业学分要求", fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = if (expanded.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded.value) "收起" else "展开"
                )
            }

            AnimatedVisibility(visible = expanded.value) {
                CreditTable(summary)
            }
        }
    }
}
@Composable
fun ScoreDetailSection(scores: List<CourseScore>) {
    val grouped = scores.groupBy { it.term }.toSortedMap(compareByDescending { it })
    val terms = grouped.keys.toList()
    var selectedIndex by remember { mutableIntStateOf(0) }

    Card (Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Tab 切换学期
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                Box(
                    Modifier
                        .tabIndicatorOffset(tabPositions[selectedIndex])
                        .height(3.dp)
                        .padding(horizontal = 16.dp)
                        .background(
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(50)
                        )
                )
            },
            containerColor = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.primary
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
                    unselectedContentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 当前学期成绩列表

        val scoresInTerm = grouped[terms[selectedIndex]].orEmpty()
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            scoresInTerm.forEach { CourseScoreItem(it) }
        }

    }
}

@Composable
fun CreditTable(summary: ScoreSummary) {
    Column(Modifier.fillMaxWidth()) {
        // 表头
        Row(
            Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("类别", "要求", "完成", "欠学分").forEachIndexed { i, title ->
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(if (i == 0) 1.5f else 1f),
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End
                )
            }
        }
        HorizontalDivider()

        // 行数据
        listOf(
            "公共任选" to summary.publicElective,
            "经管法学" to summary.mgmtLawElective,
            "人文艺术" to summary.humanitiesArtElective,
            "科学技术" to summary.scienceTechElective,
            "身心健康" to summary.healthElective,
            "学科任选" to summary.disciplineElective,
            "专业任选" to summary.majorElective
        ).forEach { (label, credits) ->
            Row(
                Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, Modifier.weight(1.5f))
                Text("${credits.required}", Modifier.weight(1f), textAlign = TextAlign.End)
                Text("${credits.completed}", Modifier.weight(1f), textAlign = TextAlign.End)
                Text("${credits.owed}", Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
fun RequirementCreditRow(label: String, credits: RequirementCredits) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1.5f))
        Text("${credits.required}", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text("${credits.completed}", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text("${credits.owed}", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}
@Composable
fun CourseScoreItem(score: CourseScore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = score.courseName.ifBlank { "(课程名未知)" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Text(
                text = score.score ?: "--",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "代码: ${score.courseId ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "类型: ${score.requirementType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "学分: ${score.credits}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "考核: ${score.assessmentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (score.retakeScore != null || score.resitScore != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
            ) {
                if (score.retakeScore != null) {
                    Text(
                        "重考: ${score.retakeScore}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (score.resitScore != null) {
                    Text(
                        "重修: ${score.resitScore}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}