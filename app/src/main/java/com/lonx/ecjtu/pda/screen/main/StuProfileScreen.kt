package com.lonx.ecjtu.pda.screen.main

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
import androidx.compose.material3.HorizontalDivider
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
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.service.StuProfileService
import com.lonx.ecjtu.pda.state.UiState
import com.lonx.ecjtu.pda.viewmodel.StuProfileViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StuProfileScreen(
    internalNavController: NavHostController,
    topLevelNavController : NavHostController,
    padding: PaddingValues,
//    scrollBehavior: UpdatableScrollBehavior,
    viewModel: StuProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        if (uiState is UiState.Empty && uiState !is UiState.Loading) {
            viewModel.load()
        }
    }
    LaunchedEffect(uiState, pullToRefreshState.isRefreshing) {
        if (uiState !is UiState.Loading && pullToRefreshState.isRefreshing) {
            pullToRefreshState.completeRefreshing(
                block = {  }
            )

        }
    }


    PullToRefresh(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding()),
        pullToRefreshState = pullToRefreshState,
        refreshTexts = listOf(
            "下拉刷新个人信息",
            "松手刷新",
            "刷新中...",
            "刷新结束"
        ),
        onRefresh = {
            viewModel.load()
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())

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
                    val message = (uiState as UiState.Error).message
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
                                text = message,
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
                    val data = (uiState as UiState.Success).data
                    item{
                        StuProfileCard(data)


                    }
                }

                else -> {
                    if (!pullToRefreshState.isRefreshing) {
                        item {
                            Text(
                                "下拉以加载个人信息",
                                modifier = Modifier.padding(32.dp),
                                style = MiuixTheme.textStyles.body2,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        item {
                            Box(modifier = Modifier
                                .fillParentMaxSize()
                                .padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

}
@Composable
fun StuProfileCard(infoData: Map<String, Map<String, String>>) {
    val categoryOrder = listOf(StuProfileService.CATEGORY_BASIC_INFO, StuProfileService.CATEGORY_CONTACT_INFO)

    if (infoData.values.all { it.isEmpty() }) {
        Text("未能加载学生信息", modifier = Modifier.padding(16.dp))
        return
    }

    Column {
        categoryOrder.forEach { categoryTitle ->
            val categoryItems = infoData[categoryTitle] ?: emptyMap()

            if (categoryItems.isNotEmpty()) {
                SmallTitle(text = categoryTitle)
                val itemsList = categoryItems.entries.toList()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    itemsList.forEachIndexed { index, entry ->
                        var (label, value) = entry
                        if (value.isEmpty()){
                            value = "暂无"
                        }
                        StuProfileItem(label = label, value = value)

                        if (index < itemsList.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }

            }
        }
    }

}
@Composable
fun StuProfileItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.main,
            textAlign = TextAlign.Start
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.subtitle,
            textAlign = TextAlign.End
        )
    }
}
