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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.ecjtu.pda.state.UiState
import com.lonx.ecjtu.pda.viewmodel.StuSecondCreditViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StuSecondCreditScreen(
    onBack: () -> Unit,
    viewModel: StuSecondCreditViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 启动时触发加载
    LaunchedEffect(Unit) {
        if (uiState !is UiState.Success && uiState !is UiState.Loading && uiState !is UiState.Error) {
            viewModel.load()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
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
                val data = state.data

                val sortedYearlyCredits = data.yearlyCredits
                    .takeIf { it.isNotEmpty() }
                    ?.sortedByDescending { it.academicYear }
                    ?: emptyList()

                if (data.totalCreditsByCategory.isEmpty() && data.yearlyCredits.isEmpty()) {
                    item {
                        Text(
                            "暂无素质拓展学分记录。",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    if (data.totalCreditsByCategory.isNotEmpty()) {
                        item {
                            CreditSectionCard(
                                title = "总素拓学分",
                                credits = data.totalCreditsByCategory
                            )
                        }
                    }

                    if (sortedYearlyCredits.isNotEmpty()) {
                        items(sortedYearlyCredits, key = { it.academicYear }) { yearlyData ->
                            CreditSectionCard(
                                title = "${yearlyData.academicYear} 学年学分记录",
                                credits = yearlyData.creditsByCategory
                            )
                        }
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

            UiState.Empty -> {
            }
        }
    }
}

@Composable
private fun CreditSectionCard(
    title: String,
    credits: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    SmallTitle(title)
    Card(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
            if (credits.isEmpty()) {
                Text(
                    "无该项记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
                    credits.entries.sortedBy { it.key }.forEach { (category, value) ->
                        Row(
                            Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = category,
                                Modifier.weight(2f), textAlign = TextAlign.Start
                            )
                            Text(
                                text = value.toString(),
                                Modifier.weight(1f), textAlign = TextAlign.End
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

}