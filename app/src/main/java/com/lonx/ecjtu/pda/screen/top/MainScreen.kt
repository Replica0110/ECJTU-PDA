package com.lonx.ecjtu.pda.screen.top

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lonx.ecjtu.pda.data.MainRoute
import com.lonx.ecjtu.pda.screen.main.HomeScreen
import com.lonx.ecjtu.pda.screen.main.JwxtScreen
import com.lonx.ecjtu.pda.screen.main.SettingScreen
import com.lonx.ecjtu.pda.screen.main.StuProfileScreen
import com.lonx.ecjtu.pda.screen.main.WifiScreen
import kotlinx.coroutines.launch
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainScreen(
    topLevelNavController: NavHostController,
    internalNavController: NavHostController = rememberNavController()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val internalNavBackStackEntry by internalNavController.currentBackStackEntryAsState()

    val currentInternalRouteString = internalNavBackStackEntry?.destination?.route

    val currentMainRoute = remember(currentInternalRouteString) {
        MainRoute.find(currentInternalRouteString)
    }
    val context = LocalContext.current

    // 由子屏幕通过回调更新
    var currentTitle by remember { mutableStateOf("花椒PDA") }
    // 由子屏幕通过回调更新，决定是否允许手势打开抽屉
    var allowDrawerGestures by remember { mutableStateOf(true) } // 初始为 true

    // 不再需要在 MainScreen 中根据 currentMainRoute 更新标题，交给子屏幕处理
    // LaunchedEffect(currentMainRoute) { ... } // 可以移除或简化

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // 修改退出逻辑：只有在允许侧边栏手势（即处于顶级页面）时才响应退出
    val shouldExit = !drawerState.isOpen && allowDrawerGestures && currentMainRoute in MainRoute.topLevelRoutesForExit
    BackHandler(enabled = shouldExit) {
        Timber.e("Back pressed on top level, exiting.")
        (context as? Activity)?.finish()
    }

    ModalNavigationDrawer(
        gesturesEnabled = drawerState.isOpen || allowDrawerGestures,
        modifier = Modifier.fillMaxSize(),
        drawerContent = {
            SideBarContent(
                internalNavController = internalNavController,
                currentMainRoute = currentMainRoute,
                onItemClick = { scope.launch { drawerState.close() } }
            )
        },
        drawerState = drawerState,
    ) {
        MainContent(
            internalNavController = internalNavController,
            topLevelNavController = topLevelNavController,
            onAllowDrawerGestureChange = { allow -> allowDrawerGestures = allow },
            currentScreenTitle = currentTitle,
            onTitleChange = { newTitle -> currentTitle = newTitle },
            onMenuClick = {
                scope.launch {
                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                }
            }
        )
    }
}

@Composable
fun SideBarContent(
    internalNavController: NavHostController,
    currentMainRoute: MainRoute?,
    onItemClick: () -> Unit
) {
    // Drawer 导航选项：导航到顶级目标时，清空栈顶直到起始目标，并确保单例、恢复状态
    val navOptionsBuilder: NavOptionsBuilder.() -> Unit = {
        popUpTo(internalNavController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }

    ModalDrawerSheet(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        drawerContainerColor = MiuixTheme.colorScheme.background // 使用主题颜色
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) // 适配状态栏
        Spacer(modifier = Modifier.height(16.dp))
        MainRoute.drawerItems.forEachIndexed { _, item ->
            when (item) {
                MainRoute.Home -> {
                    NavigationDrawerItem(
                        icon = {
                            when {
                                item.icon != null -> {
                                    Icon(item.icon, contentDescription = item.title)
                                }
                                item.painterResId != null -> {
                                    Icon(
                                        painter = painterResource(id = item.painterResId),
                                        contentDescription = item.title
                                    )
                                }
                            }
                        },
                        label = { Text(item.title) },
                        selected = currentMainRoute == item,
                        onClick = {
                            if (currentMainRoute != item) {
                                internalNavController.navigate(item.route, navOptionsBuilder)
                            }
                            onItemClick()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                MainRoute.Jwxt -> {
                    NavigationDrawerItem(
                        icon = {
                            when {
                                item.icon != null -> {
                                    Icon(item.icon, contentDescription = item.title)
                                }
                                item.painterResId != null -> {
                                    Icon(
                                        painter = painterResource(id = item.painterResId),
                                        contentDescription = item.title
                                    )
                                }
                            }
                        },
                        label = { Text(item.title) },
                        selected = currentMainRoute == item,
                        onClick = {
                            if (currentMainRoute != item) {
                                internalNavController.navigate(item.route, navOptionsBuilder)
                            }
                            onItemClick()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )


                }
                MainRoute.Profile -> {
                    NavigationDrawerItem(
                        icon = {
                            when {
                                item.icon != null -> {
                                    Icon(item.icon, contentDescription = item.title)
                                }
                                item.painterResId != null -> {
                                    Icon(
                                        painter = painterResource(id = item.painterResId),
                                        contentDescription = item.title
                                    )
                                }
                            }
                        },
                        label = { Text(item.title) },
                        selected = currentMainRoute == item,
                        onClick = {
                            if (currentMainRoute != item) {
                                internalNavController.navigate(item.route, navOptionsBuilder)
                            }
                            onItemClick()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                MainRoute.Settings -> {
                    NavigationDrawerItem(
                        icon = {
                            when {
                                item.icon != null -> {
                                    Icon(item.icon, contentDescription = item.title)
                                }
                                item.painterResId != null -> {
                                    Icon(
                                        painter = painterResource(id = item.painterResId),
                                        contentDescription = item.title
                                    )
                                }
                            }
                        },
                        label = { Text(item.title) },
                        selected = currentMainRoute == item,
                        onClick = {
                            if (currentMainRoute != item) {
                                internalNavController.navigate(item.route, navOptionsBuilder)
                            }
                            onItemClick()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                }
                MainRoute.Wifi -> {
                    NavigationDrawerItem(
                        icon = {
                            when {
                                item.icon != null -> {
                                    Icon(item.icon, contentDescription = item.title)
                                }
                                item.painterResId != null -> {
                                    Icon(
                                        painter = painterResource(id = item.painterResId),
                                        contentDescription = item.title
                                    )
                                }
                            }
                        },
                        label = { Text(item.title) },
                        selected = currentMainRoute == item,
                        onClick = {
                            if (currentMainRoute != item) {
                                internalNavController.navigate(item.route, navOptionsBuilder)
                            }
                            onItemClick()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                            unselectedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    currentScreenTitle: String,
    onTitleChange: (String) -> Unit,
    onAllowDrawerGestureChange: (Boolean) -> Unit,
    onMenuClick: () -> Unit
) {

    var currentTopAppBarNavigationIcon by remember {
        mutableStateOf<@Composable () -> Unit>({})
    }

    val menuIconComposable: @Composable () -> Unit = {
        IconButton(modifier=Modifier.padding(start = 20.dp),onClick = onMenuClick) {
            Icon(imageVector = Icons.Default.Menu, contentDescription = "打开侧边栏")
        }
    }
    LaunchedEffect(internalNavController.currentBackStackEntry) {
        // 当 internalNavController 的路由变化时（切换主模块），尝试重置
        // 注意：这可能在子模块设置图标后被错误重置，更好的方式是在每个 composable 中明确设置
        // currentTopAppBarNavigationIcon = menuIconComposable // 暂时注释掉，让子 Composable 控制
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = currentScreenTitle,
                navigationIcon = currentTopAppBarNavigationIcon
            )
        },
        content = { innerPadding ->
            NavHost(
                navController = internalNavController,
                startDestination = MainRoute.Home.route,
                modifier = Modifier
                    .fillMaxSize()

            ) {
                composable(MainRoute.Home.route) {
                    LaunchedEffect(Unit) {
                        onTitleChange(MainRoute.Home.title)
                        currentTopAppBarNavigationIcon = menuIconComposable
                        onAllowDrawerGestureChange(true)
                    }
                    HomeScreen(
                        internalNavController = internalNavController,
                        topLevelNavController = topLevelNavController,
                        padding = innerPadding
                    )
                }

                composable(MainRoute.Jwxt.route) {
                    JwxtScreen(
                        internalNavController = internalNavController, // 可能不需要？除非要跳出 Jwxt
                        topLevelNavController = topLevelNavController,
                        padding = innerPadding,
                        onTitleChange = onTitleChange, // 传递回调
                        setNavigationIcon = { iconComposable -> // 传递回调
                            currentTopAppBarNavigationIcon = iconComposable
                        },
                        onAllowDrawerGestureChange = onAllowDrawerGestureChange, // 传递回调
                        onMenuClick = onMenuClick // 传递回调，用于 JwxtMenuScreen 设置菜单按钮
                    )
                }

                composable(MainRoute.Wifi.route) {
                    LaunchedEffect(Unit) {
                        onTitleChange(MainRoute.Wifi.title)
                        currentTopAppBarNavigationIcon = menuIconComposable
                        onAllowDrawerGestureChange(true)
                    }
                    WifiScreen(
                        internalNavController = internalNavController,
                        padding = innerPadding,
                    )
                }
                composable(MainRoute.Settings.route) {
                    LaunchedEffect(Unit) {
                        onTitleChange(MainRoute.Settings.title)
                        currentTopAppBarNavigationIcon = menuIconComposable
                        onAllowDrawerGestureChange(true)
                    }
                    SettingScreen(padding = innerPadding)
                }
                composable(MainRoute.Profile.route) {
                    LaunchedEffect(Unit) {
                        onTitleChange(MainRoute.Profile.title)
                        currentTopAppBarNavigationIcon = menuIconComposable
                        onAllowDrawerGestureChange(true)
                    }
                    StuProfileScreen(
                        internalNavController = internalNavController,
                        topLevelNavController = topLevelNavController,
                        padding = innerPadding
                    )
                }
            }
        }
    )
}
