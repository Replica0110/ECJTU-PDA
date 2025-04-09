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
import com.lonx.ecjtu.pda.screen.main.JwxtScreen
import com.lonx.ecjtu.pda.screen.main.HomeScreen
import com.lonx.ecjtu.pda.screen.main.SettingScreen
import com.lonx.ecjtu.pda.screen.main.StuInfoScreen
import com.lonx.ecjtu.pda.screen.main.WifiScreen
import kotlinx.coroutines.launch
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

    var currentTitle by remember { mutableStateOf("花椒PDA") }

    LaunchedEffect(currentMainRoute) {
        if (currentMainRoute != MainRoute.Jwxt) {
            currentTitle = currentMainRoute?.title ?: "花椒PDA"
        }
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }



    val shouldExit = !drawerState.isOpen && currentMainRoute in MainRoute.topLevelRoutesForExit
    BackHandler(enabled = shouldExit) {
        (context as? Activity)?.finish()
    }

    ModalNavigationDrawer(
        gesturesEnabled = drawerState.isOpen, // 只允许抽屉打开时才允许手势关闭
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
            isWideScreen = false,
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
    isWideScreen: Boolean,
    currentScreenTitle: String,
    onTitleChange: (String) -> Unit,
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
                        currentTopAppBarNavigationIcon = menuIconComposable
                    }
                    HomeScreen(
                        internalNavController = internalNavController,
                        topLevelNavController = topLevelNavController,
                        padding = innerPadding
                    )
                }

                composable(MainRoute.Jwxt.route) {
                    JwxtScreen(
                        internalNavController = internalNavController,
                        topLevelNavController = topLevelNavController,
                        padding = innerPadding,
                        onTitleChange = onTitleChange,
                        setNavigationIcon = { iconComposable ->
                            currentTopAppBarNavigationIcon = iconComposable
                        },
                        onMenuClick = onMenuClick
                    )
                }

                composable(MainRoute.Wifi.route) {
                    LaunchedEffect(Unit) {
                        currentTopAppBarNavigationIcon = menuIconComposable
                    }
                    WifiScreen(
                        internalNavController = internalNavController,
                        padding = innerPadding,
                    )
                }
                composable(MainRoute.Settings.route) {
                    LaunchedEffect(Unit) {
                        currentTopAppBarNavigationIcon = menuIconComposable
                    }
                    SettingScreen(padding = innerPadding)
                }
                composable(MainRoute.Profile.route) {
                    LaunchedEffect(Unit) {
                        currentTopAppBarNavigationIcon = menuIconComposable
                    }
                    StuInfoScreen(
                        internalNavController = internalNavController,
                        topLevelNavController = topLevelNavController,
                        padding = innerPadding
                    )
                }
            }
        }
    )
}
