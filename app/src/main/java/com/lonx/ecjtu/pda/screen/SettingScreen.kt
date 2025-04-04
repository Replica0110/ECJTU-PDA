package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.viewmodel.SettingViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    padding: PaddingValues,
    scrollBehavior: UpdatableScrollBehavior,
    settingViewModel: SettingViewModel = koinViewModel()
) {
    val uiState by settingViewModel.uiState.collectAsState()
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
                // 处理 pre-scroll 后剩余的向上滚动 (available.y < 0)
                // 或处理向下的滚动 (available.y > 0) 以便 AppBar 展开
                if (available.y != 0f) {
                    scrollBehavior.updateHeightOffset(available.y)
                    // 这里通常不消耗 post-scroll 的 available 部分
                }
                // 可以选择在这里更新 contentOffset (如果需要)
                // scrollBehavior.updateContentOffset(consumed.y)
                return Offset.Zero
            }

            // 如果需要处理 fling，可以在这里获取速度，然后可能调用 scrollBehavior 上的 settle 方法
            // override suspend fun onPreFling(available: Velocity): Velocity { ... }
            // override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity { ... }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
        contentPadding = padding
    ){
        item {
            Card(
                modifier = Modifier
                    .padding(12.dp)
            ) {
                SuperSwitch(
                    title = "Show FPS Monitor",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Top App Bar",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Bottom Bar",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Floating Action Button",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Enable Page User Scroll",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperDropdown(
                    title = "Color Mode",
                    items = listOf("System", "Light", "Dark"),
                    selectedIndex = 1,
                    onSelectedIndexChange = {  }
                )
            }
            Card(
                modifier = Modifier
                    .padding(12.dp)
            ) {
                SuperSwitch(
                    title = "Show FPS Monitor",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Top App Bar",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Bottom Bar",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Floating Action Button",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Enable Page User Scroll",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperDropdown(
                    title = "Color Mode",
                    items = listOf("System", "Light", "Dark"),
                    selectedIndex = 1,
                    onSelectedIndexChange = {  }
                )
            }
            Card(
                modifier = Modifier
                    .padding(12.dp)
            ) {
                SuperSwitch(
                    title = "Show FPS Monitor",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Top App Bar",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Bottom Bar",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Show Floating Action Button",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperSwitch(
                    title = "Enable Page User Scroll",
                    checked = false,
                    onCheckedChange = {  }
                )
                SuperDropdown(
                    title = "Color Mode",
                    items = listOf("System", "Light", "Dark"),
                    selectedIndex = 1,
                    onSelectedIndexChange = {  }
                )
            }
        }
    }
}
