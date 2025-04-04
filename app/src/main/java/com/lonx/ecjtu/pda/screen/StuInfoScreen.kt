package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.ui.StuInfoCard
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.viewmodel.StuInfoViewModel
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Text
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
    LaunchedEffect(Unit) {
        if (uiState.studentInfo == null && !uiState.isLoading && uiState.error == null) {
            stuInfoViewModel.loadStudentInfo()
        }
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 当内容向上滚动 (available.y < 0) 时，通知 Behavior 更新状态
                if (available.y < 0) {
                    val consumedY = scrollBehavior.updateHeightOffset(available.y)
                    // 如果消耗了滚动，返回消耗的部分
                    if (consumedY != 0f) {
                        return Offset(0f, consumedY)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    scrollBehavior.updateHeightOffset(available.y)
                }
                return Offset.Zero
            }

        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .padding(bottom = 16.dp),
        contentPadding = padding
    ) {

            when {
                uiState.isLoading -> {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column {
                                CircularProgressIndicator()
                                Text(
                                    text = "如果加载时间较长，可能登录已过期，应用正在重新登录",
                                    color = Color.Gray,
                                    style = MiuixTheme.textStyles.main,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 16.dp)
                                )
                            }
                        }
                    }
                }

                uiState.error != null -> {
                   item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(vertical = 32.dp),
                            ){
                                Text(
                                    text = uiState.error!!,
                                    color = MiuixTheme.colorScheme.primary,
                                    style = MiuixTheme.textStyles.main,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { stuInfoViewModel.retryLoadStudentInfo() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp),
                                    ) {
                                    Text("重试")
                                }
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
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp + padding.calculateBottomPadding()),
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
                    item{ Text("请稍候...") }
                }
            }
        }

}
