package com.lonx.ecjtu.pda.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.utils.rememberAppBarNestedScrollConnection
import com.lonx.ecjtu.pda.viewmodel.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.theme.MiuixTheme


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun HomeScreen(
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    scrollBehavior : UpdatableScrollBehavior,
    padding:PaddingValues,

    homeViewModel: HomeViewModel = koinViewModel()
) {
    val uiState = homeViewModel.uiState.collectAsStateWithLifecycle()

    val nestedScrollConnection = rememberAppBarNestedScrollConnection(
        scrollBehavior = scrollBehavior
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = padding
    ) {
        item {
            Card(modifier = Modifier.padding(16.dp)){

                        Text(
                            text = "欢迎使用",
                            style = MiuixTheme.textStyles.title1
                        )

                }

        }
    }
}