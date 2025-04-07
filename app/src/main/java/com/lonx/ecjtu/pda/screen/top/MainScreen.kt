package com.lonx.ecjtu.pda.screen.top

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.screen.main.HomeScreen
import com.lonx.ecjtu.pda.screen.main.SettingScreen
import com.lonx.ecjtu.pda.screen.main.StuInfoScreen
import com.lonx.ecjtu.pda.screen.main.WifiScreen
import com.lonx.ecjtu.pda.screen.jwxt.JwxtScreen
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.utils.rememberNavHostAwareScrollBehavior
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainScreen(
    topLevelNavController: NavHostController,
    internalNavController: NavHostController,
    jwxtNavController: NavHostController
) {
    val sidebarWidth = 220.dp
    val wideScreenThreshold = 600.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= wideScreenThreshold

        val sidebarWidthPx = with(LocalDensity.current) { sidebarWidth.toPx() }
        val animationSpec = tween<Float>(durationMillis = 300)
        val mainContentOffsetX = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        val isOpen by remember {
            derivedStateOf { mainContentOffsetX.value > 0f }
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

        BackHandler(enabled = !isWideScreen && isOpen) {
            closeSidebar()
        }

        val navHostAwareScrollBehavior = rememberNavHostAwareScrollBehavior()
        val hazeState = remember { HazeState() }
        val showTopAppBar = remember { mutableStateOf(true) }
        val navBackStackEntry by internalNavController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val sidebarItemClickAction: () -> Unit = if (isWideScreen) {
            {  }
        } else {
            { closeSidebar() }
        }

        if (isWideScreen) {
            Row(Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .safeContentPadding()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .requiredWidth(sidebarWidth)
                        .background(MiuixTheme.colorScheme.background)
                ) {
                    SidebarContent(
                        modifier = Modifier.fillMaxSize(),
                        internalNavController = internalNavController,
                        currentRoute = currentRoute,
                        onItemClick = sidebarItemClickAction
                    )
                }

                VerticalDivider(modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp))

                MainContent(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    internalNavController = internalNavController,
                    topLevelNavController = topLevelNavController,
                    jwxtNavController = jwxtNavController,
                    currentRoute = currentRoute,
                    hazeState = hazeState,
                    navHostAwareScrollBehavior = navHostAwareScrollBehavior,
                    showTopAppBar = showTopAppBar.value,
                    isWideScreen = true,
                    onMenuClick = { },
                    onContentAreaClick = {  },
                    contentAreaClickEnabled = false,
                    topBarHazeStyle = HazeStyle(
                        backgroundColor = MiuixTheme.colorScheme.background,
                        tint = HazeTint(MiuixTheme.colorScheme.background.copy(0.67f))
                    )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background)
                    .safeContentPadding()
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
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .requiredWidth(sidebarWidth)
                        .offset {
                            IntOffset(
                                (mainContentOffsetX.value - sidebarWidthPx).roundToInt(),
                                0
                            )
                        }
                        .background(MiuixTheme.colorScheme.background)
                ) {
                    SidebarContent(
                        modifier = Modifier.fillMaxSize(),
                        internalNavController = internalNavController,
                        currentRoute = currentRoute,
                        onItemClick = sidebarItemClickAction
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .offset { IntOffset(mainContentOffsetX.value.roundToInt(), 0) }
                )


                MainContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(mainContentOffsetX.value.roundToInt(), 0) },
                    internalNavController = internalNavController,
                    topLevelNavController = topLevelNavController,
                    jwxtNavController = jwxtNavController,
                    currentRoute = currentRoute,
                    hazeState = hazeState,
                    navHostAwareScrollBehavior = navHostAwareScrollBehavior,
                    showTopAppBar = showTopAppBar.value,
                    isWideScreen = false,
                    onMenuClick = { if (isFullyOpen) closeSidebar() else openSidebar() },
                    onContentAreaClick = { if (isFullyOpen) closeSidebar() },
                    contentAreaClickEnabled = isFullyOpen,
                    topBarHazeStyle = HazeStyle(
                        backgroundColor = MiuixTheme.colorScheme.background,
                        tint = HazeTint(
                            MiuixTheme.colorScheme.background.copy(
                                if (navHostAwareScrollBehavior.state.collapsedFraction <= 0f) 1f
                                else lerp(1f, 0.67f, (navHostAwareScrollBehavior.state.collapsedFraction))
                            )
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun SidebarContent(
    modifier: Modifier = Modifier,
    internalNavController: NavHostController,
    currentRoute: String?,
    onItemClick: () -> Unit
) {
    val navOptionsBuilder: androidx.navigation.NavOptionsBuilder.() -> Unit = {
        popUpTo(internalNavController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    LazyColumn(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        item {
            Card {
                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = "个人信息") },
                    label = { Text("个人信息") },
                    selected = currentRoute == AppRoutes.PROFILE,
                    onClick = {
                        internalNavController.navigate(AppRoutes.PROFILE, navOptionsBuilder)
                        onItemClick()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Card {
                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "主页") },
                    label = { Text("主页") },
                    selected = currentRoute == AppRoutes.HOME,
                    onClick = {
                        internalNavController.navigate(AppRoutes.HOME, navOptionsBuilder)
                        onItemClick()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
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
                        internalNavController.navigate(AppRoutes.JWXT, navOptionsBuilder)
                        onItemClick()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_wifi),
                            contentDescription = "校园网"
                        )
                    },
                    label = { Text("校园网") },
                    selected = currentRoute == AppRoutes.WIFI,
                    onClick = {
                        internalNavController.navigate(AppRoutes.WIFI, navOptionsBuilder)
                        onItemClick()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Card {
                NavigationDrawerItem(
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = currentRoute == AppRoutes.SETTING,
                    onClick = {
                        internalNavController.navigate(AppRoutes.SETTING, navOptionsBuilder)
                        onItemClick()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MiuixTheme.colorScheme.secondaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    internalNavController: NavHostController,
    topLevelNavController: NavHostController,
    jwxtNavController: NavHostController,
    currentRoute: String?,
    hazeState: HazeState,
    navHostAwareScrollBehavior: UpdatableScrollBehavior,
    showTopAppBar: Boolean,
    isWideScreen: Boolean,
    onMenuClick: () -> Unit,
    onContentAreaClick: () -> Unit,
    contentAreaClickEnabled: Boolean,
    topBarHazeStyle: HazeStyle
) {
    val interactionSource = remember { MutableInteractionSource() }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier,
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
                visible = showTopAppBar,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                    SmallTopAppBar(
                        title = currentScreenTitle,
                        modifier = Modifier.hazeEffect(state = hazeState) {
                            style = topBarHazeStyle
                            blurRadius = 25.dp
                            noiseFactor = 0f
                        },
                        scrollBehavior = navHostAwareScrollBehavior,
                        color = Color.Transparent,
                        navigationIcon = {
                            if (!isWideScreen) {
                                IconButton(
                                    modifier = Modifier.padding(start = 20.dp),
                                    onClick = onMenuClick,
                                ) {
                                    top.yukonga.miuix.kmp.basic.Icon(
                                        imageVector = Icons.Default.Menu,
                                        tint = MiuixTheme.colorScheme.onBackground,
                                        contentDescription = "侧边栏"
                                    )
                                }
                            }
                        }
                    )
            }
        },
        content = { innerPadding ->
            NavHost(
                navController = internalNavController,
                startDestination = AppRoutes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        enabled = contentAreaClickEnabled,
                        onClick = onContentAreaClick,
                        indication = null,
                        interactionSource = interactionSource
                    )
                    .hazeSource(state = hazeState)
            ) {
                composable(AppRoutes.HOME) { HomeScreen(internalNavController=internalNavController, topLevelNavController = topLevelNavController, scrollBehavior = navHostAwareScrollBehavior, padding = innerPadding) }
                composable(AppRoutes.JWXT) { JwxtScreen(internalNavController = internalNavController, topLevelNavController = topLevelNavController, jwxtNavController = jwxtNavController, scrollBehavior = navHostAwareScrollBehavior, padding = innerPadding) }
                composable(AppRoutes.WIFI) { WifiScreen(internalNavController = internalNavController, padding = innerPadding, scrollBehavior = navHostAwareScrollBehavior) }
                composable(AppRoutes.SETTING) { SettingScreen(padding = innerPadding, scrollBehavior = navHostAwareScrollBehavior) }
                composable(AppRoutes.PROFILE) { StuInfoScreen(internalNavController = internalNavController, topLevelNavController = topLevelNavController, padding = innerPadding, scrollBehavior = navHostAwareScrollBehavior) }
            }
        }
    )
}

