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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lonx.ecjtu.pda.data.CourseScore
import com.lonx.ecjtu.pda.data.RequirementCredits
import com.lonx.ecjtu.pda.data.ScoreSummary
import com.lonx.ecjtu.pda.data.StudentScoreData
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.utils.rememberAppBarNestedScrollConnection
import com.lonx.ecjtu.pda.viewmodel.StuScoreViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

@Composable
fun StuScoreScreen(
    onBack: () -> Unit,
    scrollBehavior: UpdatableScrollBehavior,
    viewModel: StuScoreViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState.studentScoreData == null && !uiState.isLoading) {
            viewModel.loadScores()
        }
    }

    val nestedScrollConnection = rememberAppBarNestedScrollConnection(scrollBehavior)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
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

fun LazyListScope.scoreContent(data: StudentScoreData) {
    val groupedScores = data.detailedScores
        .groupBy { it.term }
        .toSortedMap(compareByDescending { term -> term })

    // 成绩摘要部分
    item {
        Column {
            SmallTitle("绩点总览")
            ScoreSummaryCard(summary = data.summary)

        }
        SmallTitle("详细成绩")
    }

    if (groupedScores.isEmpty()) {
        item {
            Text(
                "暂无详细成绩记录。",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        groupedScores.forEach { (term, scores) ->
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MiuixTheme.colorScheme.surfaceVariant,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = "学期: $term",
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            items(scores, key = { it.courseId ?: UUID.randomUUID().toString() }) { score ->
                CourseScoreItem(score = score)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}


@Composable
fun ScoreSummaryCard(summary: ScoreSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("学位绩点要求: ${summary.gpaRequired ?: "N/A"}")
                Text("已获绩点: ${summary.gpaAchieved ?: "N/A"}")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 表头行
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("类别", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text("要求", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                Text("完成", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                Text("欠学分", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            }


            // 每个类别行
            RequirementCreditRow("公共任选", summary.publicElective)
            RequirementCreditRow("经管法学", summary.mgmtLawElective)
            RequirementCreditRow("人文艺术", summary.humanitiesArtElective)
            RequirementCreditRow("科学技术", summary.scienceTechElective)
            RequirementCreditRow("身心健康", summary.healthElective)
            RequirementCreditRow("学科任选", summary.disciplineElective)
            RequirementCreditRow("专业任选", summary.majorElective)

            summary.academicWarningRequired?.let { req ->
                Divider(modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("学业预警要求: $req")
                    Text("已完成: ${summary.academicWarningCompleted ?: "N/A"}")
                }
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
            .padding(vertical = 12.dp, horizontal = 10.dp)
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