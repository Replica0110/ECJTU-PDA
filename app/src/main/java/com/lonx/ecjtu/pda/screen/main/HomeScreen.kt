package com.lonx.ecjtu.pda.screen.main

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.utils.rememberAppBarNestedScrollConnection
import com.lonx.ecjtu.pda.viewmodel.HomeViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
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
    Scaffold {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            contentPadding = padding
        ) {
            item {
                Card(modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)) {

                    Text(
                        text = "开发中",
                        style = MiuixTheme.textStyles.title1
                    )

                }
            }
        }
    }
}