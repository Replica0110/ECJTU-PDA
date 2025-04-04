package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.ui.StuInfoCard
import com.lonx.ecjtu.pda.viewmodel.StuInfoViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StuInfoScreen(
    internalNavController: NavHostController,
    topLevelNavController : NavHostController,
    stuInfoViewModel: StuInfoViewModel = koinViewModel()
) {
    val scrollState = rememberScrollState()
    val uiState by stuInfoViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (uiState.studentInfo == null && !uiState.isLoading && uiState.error == null) {
            stuInfoViewModel.loadStudentInfo()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
    ) {
        when {
            uiState.isLoading -> {
                // 显示加载指示器
                CircularProgressIndicator()
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "错误",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { stuInfoViewModel.retryLoadStudentInfo() }) {
                        Text("重试")
                    }
                }
            }
            uiState.studentInfo != null -> {
                StuInfoCard(uiState.studentInfo!!)

                Spacer(modifier = Modifier.height(24.dp))

                FilledTonalButton(
                    modifier = Modifier,
                    onClick = {
                        stuInfoViewModel.performLogout()
                        topLevelNavController.navigate(AppRoutes.LOGIN) {
                            popUpTo(topLevelNavController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("退出登录")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            else -> {
                Text("请稍候...")
            }
        }
    }
}
