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
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.screen.jwxt.JwxtScreen
import com.lonx.ecjtu.pda.screen.main.HomeScreen
import com.lonx.ecjtu.pda.screen.main.SettingScreen
import com.lonx.ecjtu.pda.screen.main.StuInfoScreen
import com.lonx.ecjtu.pda.screen.main.WifiScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.IconButton
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
    val currentInternalRoute = internalNavBackStackEntry?.destination?.route
    val context = LocalContext.current

    var currentTitle by remember { mutableStateOf("花椒PDA") }
    LaunchedEffect(currentInternalRoute) {
        currentTitle = when (currentInternalRoute) {
            AppRoutes.HOME -> "主页"
            AppRoutes.JWXT -> "教务系统"
            AppRoutes.WIFI -> "校园网"
            AppRoutes.SETTING -> "设置"
            AppRoutes.PROFILE -> "个人信息"
            else -> currentTitle
        }
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val exitRoutes = remember {
        setOf(AppRoutes.HOME, AppRoutes.PROFILE, AppRoutes.WIFI, AppRoutes.SETTING, AppRoutes.JWXT)
    }

    BackHandler(enabled = !drawerState.isOpen && currentInternalRoute in exitRoutes) {
        (context as? Activity)?.finish()
    }

    ModalNavigationDrawer(
        gesturesEnabled = drawerState.isOpen, // 只允许抽屉打开时才允许手势关闭
        modifier = Modifier.fillMaxSize(),
        drawerContent = {
            SideBarContent(
                internalNavController = internalNavController,
                currentRoute = currentInternalRoute,
                onItemClick = { scope.launch { drawerState.close() } }
            )
        },
        drawerState = drawerState,
    ) {
        MainContent(
            internalNavController = internalNavController,
            topLevelNavController = topLevelNavController,
            hazeState = remember { HazeState() },
            isWideScreen = false,
            currentScreenTitle = currentTitle,
            onTitleChange = { newTitle -> currentTitle = newTitle },
            onMenuClick = {
                scope.launch {
                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                }
            },
            topBarHazeStyle = HazeStyle(
                backgroundColor = MiuixTheme.colorScheme.background,
                tint = HazeTint(MiuixTheme.colorScheme.background.copy(alpha = 1f))
            )
        )
    }
}

@Composable
fun SideBarContent(
    internalNavController: NavHostController,
    currentRoute: String?,
    onItemClick: () -> Unit,
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

        // 个人信息项
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = "个人信息") },
            label = { Text("个人信息") },
            selected = currentRoute == AppRoutes.PROFILE,
            onClick = {
                if (currentRoute != AppRoutes.PROFILE) {
                    internalNavController.navigate(AppRoutes.PROFILE, navOptionsBuilder)
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

        // 主要导航项
        listOf(
            Triple("主页", Icons.Outlined.Home, AppRoutes.HOME),
            Triple("教务系统", Icons.Outlined.AccountBox, AppRoutes.JWXT),
            Triple("校园网", painterResource(id = R.drawable.ic_menu_wifi), AppRoutes.WIFI)
        ).forEach { (label, icon, route) ->
            NavigationDrawerItem(
                icon = {
                    when (icon) {
                        is Painter -> Icon(painter = icon, contentDescription = label)
                        is ImageVector -> Icon(imageVector = icon, contentDescription = label)
                    }
                },
                label = { Text(label) },
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        internalNavController.navigate(route, navOptionsBuilder)
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

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(modifier = Modifier.height(8.dp))

        // 设置项
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
            label = { Text("设置") },
            selected = currentRoute == AppRoutes.SETTING,
            onClick = {
                if (currentRoute != AppRoutes.SETTING) {
                    internalNavController.navigate(AppRoutes.SETTING, navOptionsBuilder)
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

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) // 适配导航栏
    }
}


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    hazeState: HazeState,
    isWideScreen: Boolean,
    currentScreenTitle: String,
    onTitleChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    topBarHazeStyle: HazeStyle
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
                modifier = Modifier.hazeSource(
                    state = hazeState
                ),

                navigationIcon = currentTopAppBarNavigationIcon
            )
        },
        content = { innerPadding ->
            NavHost(
                navController = internalNavController,
                startDestination = AppRoutes.HOME,
                modifier = Modifier
                    .fillMaxSize()

                    .hazeEffect(state = hazeState)
            ) {
                composable(AppRoutes.HOME) {
                    LaunchedEffect(Unit) {
                        onTitleChange("主页")
                        currentTopAppBarNavigationIcon = menuIconComposable
                    }
                    HomeScreen(
                        internalNavController = internalNavController,
                        topLevelNavController = topLevelNavController,
                        padding = innerPadding
                    )
                }

                composable(AppRoutes.JWXT) {
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

                composable(AppRoutes.WIFI) {
                    LaunchedEffect(Unit) {
                        onTitleChange("校园网")
                        currentTopAppBarNavigationIcon = menuIconComposable
                    }
                    WifiScreen(
                        internalNavController = internalNavController,
                        padding = innerPadding,
                    )
                }
                composable(AppRoutes.SETTING) {
                    LaunchedEffect(Unit) {
                        onTitleChange("设置")
                        currentTopAppBarNavigationIcon = menuIconComposable
                    }
                    SettingScreen(padding = innerPadding)
                }
                composable(AppRoutes.PROFILE) {
                    LaunchedEffect(Unit) {
                        onTitleChange("个人信息")
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
