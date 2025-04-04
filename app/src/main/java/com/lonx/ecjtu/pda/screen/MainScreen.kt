package com.lonx.ecjtu.pda.screen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.AppRoutes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    topLevelNavController: NavHostController,
    internalNavController: NavHostController
) {
    val sidebarWidth = 250.dp // M3 抽屉通常稍宽一些，可以调整
    val sidebarWidthPx = with(LocalDensity.current) { sidebarWidth.toPx() }
    val animationSpec = tween<Float>(durationMillis = 300) // M3 动画通常稍慢

    val mainContentOffsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val isOpen by remember {
        derivedStateOf { mainContentOffsetX.value > 0f } // 打开状态判断调整
    }
    val isFullyOpen by remember {
        derivedStateOf { mainContentOffsetX.value == sidebarWidthPx }
    }

    fun openSidebar() {
        scope.launch { mainContentOffsetX.animateTo(sidebarWidthPx, animationSpec) }
    }

    fun closeSidebar() {
        scope.launch { mainContentOffsetX.animateTo(0f, animationSpec) }
    }

    BackHandler(enabled = isOpen) {
        closeSidebar()
    }

    val navBackStackEntry by internalNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 使用 M3 背景色
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val newOffset =
                        (mainContentOffsetX.value + delta).coerceIn(0f, sidebarWidthPx)
                    scope.launch { mainContentOffsetX.snapTo(newOffset) }
                },
                onDragStopped = { velocity ->
                    scope.launch {
                        if (velocity > 300f || (velocity > -300f && mainContentOffsetX.value > sidebarWidthPx / 2)) {
                            openSidebar()
                        } else {
                            closeSidebar()
                        }
                    }
                }
            )
    ) {

        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .requiredWidth(sidebarWidth)
                .offset { IntOffset((mainContentOffsetX.value - sidebarWidthPx).roundToInt(), 0) },
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = "个人信息") }, // 使用标准图标
                    label = { Text("个人信息") },
                    selected = currentRoute == AppRoutes.PROFILE,
                    onClick = {
                        internalNavController.navigate(AppRoutes.PROFILE) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    },
                    modifier = Modifier.padding(bottom = 8.dp) // 与下一组分隔
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // 分隔线

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "主页") },
                    label = { Text("主页") },
                    selected = currentRoute == AppRoutes.HOME, // 选中状态判断
                    onClick = {
                        internalNavController.navigate(AppRoutes.HOME) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.AccountBox, contentDescription = "教务系统") },
                    label = { Text("教务系统") },
                    selected = currentRoute == AppRoutes.JWXT,
                    onClick = {
                        internalNavController.navigate(AppRoutes.JWXT) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(painterResource(R.drawable.ic_menu_wifi), contentDescription = "校园网") },
                    label = { Text("校园网") },
                    selected = currentRoute == AppRoutes.WIFI,
                    onClick = {
                        internalNavController.navigate(AppRoutes.WIFI) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // 分隔线

                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = currentRoute == AppRoutes.SETTING,
                    onClick = {
                        internalNavController.navigate(AppRoutes.SETTING) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Info, contentDescription = "关于") },
                    label = { Text("关于") },
                    selected = currentRoute == AppRoutes.ABOUT,
                    onClick = {
                        internalNavController.navigate(AppRoutes.ABOUT) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    }
                )
            }
        }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(mainContentOffsetX.value.roundToInt(), 0) }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    val currentScreenTitle = when (currentRoute) {
                        AppRoutes.HOME -> "主页"
                        AppRoutes.JWXT -> "教务系统"
                        AppRoutes.WIFI -> "校园网"
                        AppRoutes.SETTING -> "设置"
                        AppRoutes.ABOUT -> "关于"
                        AppRoutes.PROFILE -> "个人信息"
                        else -> "华交工具箱"
                    }
                    TopAppBar(
                        title = { Text(currentScreenTitle) },
                        navigationIcon = {
                            IconButton(onClick = { if (isFullyOpen) closeSidebar() else openSidebar() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Menu,
                                    contentDescription = "打开侧边栏"
                                )
                            }
                        },
                        // 可选: 设置颜色
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            // 你也可以使用 surface, surfaceVariant 等作为 containerColor
                            // containerColor = MaterialTheme.colorScheme.surface,
                            // titleContentColor = MaterialTheme.colorScheme.onSurface,
                            // navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                    )
                },
                content = { innerPadding ->
                    NavHost(
                        navController = internalNavController,
                        startDestination = AppRoutes.HOME,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        composable(AppRoutes.HOME) { HomeScreen(internalNavController, topLevelNavController) }
                        composable(AppRoutes.JWXT) { JwxtScreen(internalNavController) }
                        composable(AppRoutes.WIFI) { WifiScreen(internalNavController) }
                        composable(AppRoutes.SETTING) { SettingScreen(
                            internalNavController,
                            topLevelNavController = topLevelNavController
                        ) }
                        composable(AppRoutes.ABOUT) { AboutScreen(internalNavController) }
                        composable(AppRoutes.PROFILE) { StuInfoScreen(internalNavController, topLevelNavController) }
                    }
                }
            )
        }
    }
}