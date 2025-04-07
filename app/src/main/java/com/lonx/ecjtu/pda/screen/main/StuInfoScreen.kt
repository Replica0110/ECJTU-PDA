package com.lonx.ecjtu.pda.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.ui.StuInfoCard
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.utils.rememberAppBarNestedScrollConnection
import com.lonx.ecjtu.pda.viewmodel.StuInfoViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StuInfoScreen(
    internalNavController: NavHostController,
    topLevelNavController : NavHostController,
    padding: PaddingValues,
    scrollBehavior: UpdatableScrollBehavior,
    stuInfoViewModel: StuInfoViewModel = koinViewModel()
) {
    val uiState by stuInfoViewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        if (uiState.studentInfo == null && !uiState.isLoading && uiState.error == null) {
            stuInfoViewModel.loadStudentInfo()
        }
    }
    LaunchedEffect(uiState.isLoading, pullToRefreshState.isRefreshing) {
        if (!uiState.isLoading && pullToRefreshState.isRefreshing) {
            pullToRefreshState.completeRefreshing(
                block = {  }
            )

        }
    }

    val nestedScrollConnection = rememberAppBarNestedScrollConnection(
        scrollBehavior = scrollBehavior,
        pullToRefreshState = pullToRefreshState
    )

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
            stuInfoViewModel.loadStudentInfo()
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())

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
                                .fillParentMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "错误",
                                tint = MiuixTheme.colorScheme.primary, // Use theme color
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.error!!,
                                color = MiuixTheme.colorScheme.primary, // Use theme error color
                                style = MiuixTheme.textStyles.main, // Use appropriate style
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp).width(60.dp))
                            Button(onClick = { stuInfoViewModel.retryLoadStudentInfo() }) {
                                Text("重试")
                            }
                        }
                    }
                }

                uiState.studentInfo != null -> {
                    item{
                        StuInfoCard(uiState.studentInfo!!)


                    }
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp + padding.calculateBottomPadding()),
                        ) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    stuInfoViewModel.performLogout()
                                    topLevelNavController.navigate(AppRoutes.LOGIN) {
                                        popUpTo(topLevelNavController.graph.findStartDestination().id) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                            ) {
                                Text("退出登录", color = Color(0xFFF44336))
                            }
                        }

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
