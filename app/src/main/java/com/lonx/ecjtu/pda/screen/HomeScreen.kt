package com.lonx.ecjtu.pda.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.viewmodel.HomeViewModel
import org.koin.androidx.compose.koinViewModel


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun HomeScreen(
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    homeViewModel: HomeViewModel = koinViewModel()
) {
    val uiState = homeViewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Button(
                onClick = {
                    homeViewModel.performLogout()
                    topLevelNavController.navigate(AppRoutes.LOGIN) {
                        popUpTo(topLevelNavController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("退出登录")
            }
            Spacer(Modifier.fillMaxWidth().padding(8.dp))
            Button(
                onClick = {
                    internalNavController.navigate(AppRoutes.SETTING) {
                        popUpTo(topLevelNavController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }

                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("账号配置")
            }
        }
    }
}