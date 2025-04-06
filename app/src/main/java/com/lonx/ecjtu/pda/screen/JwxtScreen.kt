package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.utils.rememberAppBarNestedScrollConnection
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun JwxtScreen(
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    scrollBehavior: UpdatableScrollBehavior,
    padding: PaddingValues,
) {
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