package com.lonx.ecjtu.pda.screen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.utils.rememberNavHostAwareScrollBehavior
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainScreen(
    topLevelNavController: NavHostController,
    internalNavController: NavHostController
) {
    val sidebarWidth = 200.dp
    val sidebarWidthPx = with(LocalDensity.current) { sidebarWidth.toPx() }
    val animationSpec = tween<Float>(durationMillis = 300)

    val mainContentOffsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val navHostAwareScrollBehavior = rememberNavHostAwareScrollBehavior()


    val hazeState = remember { HazeState() }

    val hazeStyleTopBar = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.background,
        tint = HazeTint(
            MiuixTheme.colorScheme.background.copy(
                if (navHostAwareScrollBehavior.state.collapsedFraction <= 0f) 1f
                else lerp(1f, 0.67f, (navHostAwareScrollBehavior.state.collapsedFraction))
            )
        )
    )

    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.background,
        tint = HazeTint(MiuixTheme.colorScheme.background.copy(0.67f))
    )

    val showTopAppBar = remember { mutableStateOf(true) }


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
            .background(MiuixTheme.colorScheme.background)
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

        Scaffold(
            containerColor = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxHeight()
                .requiredWidth(sidebarWidth)
                .offset { IntOffset((mainContentOffsetX.value - sidebarWidthPx).roundToInt(), 0) },

        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = "个人信息") },
                    label = { Text("个人信息") },
                    selected = currentRoute == AppRoutes.PROFILE,
                    onClick = {
                        internalNavController.navigate(AppRoutes.PROFILE) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    },
                    modifier = Modifier.padding(bottom = 8.dp) ,
                    colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // 分隔线

                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "主页") },
                    label = { Text("主页") },
                    selected = currentRoute == AppRoutes.HOME, // 选中状态判断
                    onClick = {
                        internalNavController.navigate(AppRoutes.HOME) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    },
                    colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(Icons.Outlined.AccountBox, contentDescription = "教务系统") },
                    label = { Text("教务系统") },
                    selected = currentRoute == AppRoutes.JWXT,
                    onClick = {
                        internalNavController.navigate(AppRoutes.JWXT) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    },
                    colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(painterResource(R.drawable.ic_menu_wifi), contentDescription = "校园网") },
                    label = { Text("校园网") },
                    selected = currentRoute == AppRoutes.WIFI,
                    onClick = {
                        internalNavController.navigate(AppRoutes.WIFI) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    },
                    colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // 分隔线

                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = currentRoute == AppRoutes.SETTING,
                    onClick = {
                        internalNavController.navigate(AppRoutes.SETTING) {
                            launchSingleTop = true
                            popUpTo(internalNavController.graph.startDestinationId) { saveState = true }
                        }
                        closeSidebar()
                    },
                    colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
            }
        }
        VerticalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Scaffold(
                containerColor = MiuixTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(mainContentOffsetX.value.roundToInt(), 0) },
                topBar = {
                    val currentScreenTitle = when (currentRoute) {
                        AppRoutes.HOME -> "主页"
                        AppRoutes.JWXT -> "教务系统"
                        AppRoutes.WIFI -> "校园网"
                        AppRoutes.SETTING -> "设置"
                        AppRoutes.PROFILE -> "个人信息"
                        else -> stringResource(R.string.app_name)
                    }
                    AnimatedVisibility(
                        visible = showTopAppBar.value,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        BoxWithConstraints {
                            if (maxWidth > 840.dp) {
                                SmallTopAppBar(
                                    title = currentScreenTitle,
                                    modifier = Modifier.hazeEffect(state = hazeState) {
                                        style = hazeStyle
                                        blurRadius = 25.dp
                                        noiseFactor = 0f
                                    },
                                    scrollBehavior = navHostAwareScrollBehavior,
                                    color = Color.Transparent,
                                    navigationIcon = {
                                        IconButton(
                                            modifier = Modifier.padding(start = 20.dp),
                                            onClick = {
                                                run { if (isFullyOpen) closeSidebar() else openSidebar() }
                                            },
                                        ) {
                                            top.yukonga.miuix.kmp.basic.Icon(
                                                imageVector = MiuixIcons.Useful.Settings,
                                                tint = MiuixTheme.colorScheme.onBackground,
                                                contentDescription = "侧边栏"
                                            )
                                        }
                                    }
                                )
                            } else {
                                TopAppBar(
                                    title = currentScreenTitle,
                                    modifier = Modifier
                                        .hazeEffect(state = hazeState) {
                                            style = hazeStyleTopBar
                                            blurRadius = 25.dp
                                            noiseFactor = 0f
                                        },
                                    scrollBehavior = navHostAwareScrollBehavior,
                                    color = Color.Transparent,
                                    navigationIcon = {
                                        IconButton(
                                            modifier = Modifier.padding(start = 20.dp),
                                            onClick = {
                                                run { if (isFullyOpen) closeSidebar() else openSidebar() }
                                            },
                                        ) {
                                            top.yukonga.miuix.kmp.basic.Icon(
                                                imageVector = MiuixIcons.Useful.Settings,
                                                tint = MiuixTheme.colorScheme.onBackground,
                                                contentDescription = "侧边栏"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                content = { innerPadding ->

                    NavHost(
                        navController = internalNavController,
                        startDestination = AppRoutes.HOME,
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = hazeState)
                    ) {
                        composable(AppRoutes.HOME) { HomeScreen(internalNavController, topLevelNavController,innerPadding) }
                        composable(AppRoutes.JWXT) { JwxtScreen(internalNavController,innerPadding) }
                        composable(AppRoutes.WIFI) { WifiScreen(internalNavController,innerPadding) }
                        composable(AppRoutes.SETTING) { SettingScreen(
                            padding = innerPadding,
                            scrollBehavior = navHostAwareScrollBehavior
                        ) }
                        composable(AppRoutes.PROFILE) { StuInfoScreen(
                            internalNavController = internalNavController,
                            topLevelNavController = topLevelNavController,
                            padding = innerPadding,
                            scrollBehavior = navHostAwareScrollBehavior
                        ) }
                    }
                }
            )

    }
}
class AppBarScrollStateHolder {
    var scrollOffset: Float by mutableFloatStateOf(0f)
        private set // 外部只能读取

    fun updateScrollOffset(newOffset: Float) {
        scrollOffset = newOffset
        // 这里可以添加逻辑计算折叠比例等
    }

    fun resetScrollOffset() {
        scrollOffset = 0f
    }
}

@Composable
fun rememberAppBarScrollStateHolder(): AppBarScrollStateHolder {
    return remember { AppBarScrollStateHolder() }
}