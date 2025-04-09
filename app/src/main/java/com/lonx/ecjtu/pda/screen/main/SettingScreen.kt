package com.lonx.ecjtu.pda.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.screen.AccountConfigDialog
import com.lonx.ecjtu.pda.screen.ChangePasswordDialog
import com.lonx.ecjtu.pda.ui.InfoAlertDialog
import com.lonx.ecjtu.pda.ui.InputAlertDialog
import com.lonx.ecjtu.pda.viewmodel.SettingUiEvent
import com.lonx.ecjtu.pda.viewmodel.SettingViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Personal
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingScreen(
    padding: PaddingValues,
//    scrollBehavior: UpdatableScrollBehavior,
    settingViewModel: SettingViewModel = koinViewModel()
) {
    val uiState by settingViewModel.uiState.collectAsState()

    // 对话框
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable  { mutableStateOf(false) }
    var showWeiXinIdDialog by rememberSaveable { mutableStateOf(false) }
    var showWeiXinIdTutorialDialog by rememberSaveable { mutableStateOf(false) }
    // 标题栏滚动
//    val nestedScrollConnection = rememberAppBarNestedScrollConnection(scrollBehavior)

    // snackBar通知
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarContainerColor by remember { mutableStateOf(Color.Unspecified) }
    var snackbarContentColor by remember { mutableStateOf(Color.Unspecified) }
    // snackBar通知颜色
    val successContainerColor = MiuixTheme.colorScheme.primaryContainer
    val successContentColor = MiuixTheme.colorScheme.onPrimaryContainer
    val errorContainerColor = MaterialTheme.colorScheme.errorContainer
    val errorContentColor = MaterialTheme.colorScheme.onError
    val defaultContainerColor = MiuixTheme.colorScheme.onSurfaceContainer
    val defaultContentColor = MiuixTheme.colorScheme.onSurface
    // 账号配置对话框
    AccountConfigDialog(
        currentStudentId = uiState.studentId,
        currentPassword = uiState.password,
        currentIsp = uiState.ispSelected,
        isLoading = uiState.isLoading,
        error = uiState.error,
        onDismissRequest = { showAccountDialog= false },
        onSave = { studentId, password, selectedIsp ->
            settingViewModel.updateConfig(studentId, password, selectedIsp) },
        show = showAccountDialog
    )
    // 修改密码对话框
    ChangePasswordDialog(
        showDialog = showPasswordDialog,
        onDismissRequest = { showPasswordDialog = false },
        onConfirm = { old, new ->
            settingViewModel.updatePassword(
                old, new
            )
        },
        initialOldPassword = uiState.password,
        confirmButtonText = "确认",
        dismissButtonText = "取消",
        isLoading = uiState.isLoading
    )

    // 修改微信ID对话框
    InputAlertDialog(
        showDialog = showWeiXinIdDialog,
        onDismissRequest = {
            showWeiXinIdDialog = false
        },
        title = "WinXinID设置",
        confirmButtonText = "保存",
        onConfirm = {
            settingViewModel.updateWeiXinId(it)
            showWeiXinIdDialog = false
        },
        label = "WeiXinID",
        initialValue = uiState.weiXinId,
        dismissButtonText = "取消",
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        )
    )
    val weiXinIdTutorial = buildAnnotatedString {
        append("·关注华交教务微信公众号\n")
        append("·绑定个人账号\n")
        append("·点击[更多功能]->[我的日历]\n")
        append("·点击右上角三点，选择[复制链接]\n")
        append("·将复制的链接粘贴到设置中\n")
        append("·图文教程请参阅")
        val tutorialUrl = "https://github.com/Replica0110/ECJTU-Calendar"
        pushLink(LinkAnnotation.Url(tutorialUrl))
        withStyle(style = SpanStyle(color = Color.DarkGray, textDecoration = TextDecoration.Underline)) {
            append("https://github.com/Replica0110/ECJTU-Calendar")
        }
        pop()
        append("\n")
    }
    // 教程对话框
    InfoAlertDialog(
        showDialog = showWeiXinIdTutorialDialog,
        onDismissRequest = {
            showWeiXinIdTutorialDialog = false
        },
        title = "获取WeiXinID",
        message = weiXinIdTutorial
    )
    LaunchedEffect(key1 = settingViewModel) {
        settingViewModel.uiEvent.collect { event ->
            when (event) {
                is SettingUiEvent.ShowSnackbar -> {
                    if (event.success) {
                        snackbarContainerColor = successContainerColor
                        snackbarContentColor = successContentColor
                    } else {
                        snackbarContainerColor = errorContainerColor
                        snackbarContentColor = errorContentColor
                    }
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is SettingUiEvent.CloseDialog -> {
                    showPasswordDialog = false
                }
            }
        }
    }
    Scaffold(
        modifier = Modifier.background(MiuixTheme.colorScheme.background),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                Snackbar(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    containerColor = snackbarContainerColor.takeOrElse { defaultContainerColor },
                    contentColor = snackbarContentColor.takeOrElse { defaultContentColor },
                    snackbarData = snackbarData
                )
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = padding
        ) {

            item {
                SmallTitle("账号")
                Card(
                    modifier = Modifier
                        .padding(12.dp)
                ) {
                    SuperArrow(
                        leftAction = {
                            Box(
                                contentAlignment = Alignment.TopStart,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Useful.Personal,
                                    contentDescription = "账号设置",
                                    tint = MiuixTheme.colorScheme.onBackground
                                )
                            }
                        },
                        title = "账号设置",
                        summary = "配置账号密码及运营商",
                        onClick = {
                            showAccountDialog = true
                        },
                        holdDownState = false
                    )
                    SuperArrow(
                        leftAction = {
                            Box(
                                contentAlignment = Alignment.TopStart,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_edit),
                                    contentDescription = "修改密码",
                                    tint = MiuixTheme.colorScheme.onBackground
                                )
                            }
                        },
                        title = "修改密码",
                        summary = "修改智慧交大密码",
                        onClick = {
                            showPasswordDialog = true
                        },
                        holdDownState = false
                    )
                    SuperArrow(
                        leftAction = {
                            Box(
                                contentAlignment = Alignment.TopStart,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_weixinid),
                                    contentDescription = "WeiXinID设置",
                                    tint = MiuixTheme.colorScheme.onBackground
                                )
                            }
                        },
                        title = "WeiXinID设置",
                        summary = "华交教务公众号WeiXinID设置",
                        onClick = {
                            showWeiXinIdDialog = true
                        },
                        holdDownState = false
                    )
                }

            }
            item {
                SmallTitle("教程")
                Card(modifier = Modifier.padding(12.dp)) {
                    SuperArrow(
                        leftAction = {
                            Box(
                                contentAlignment = Alignment.TopStart,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tutorial),
                                    contentDescription = "获取WeiXinID",
                                    tint = MiuixTheme.colorScheme.onBackground
                                )
                            }
                        },
                        title = "获取WeiXinID",
                        summary = "如何获取WeiXinID？",
                        onClick = {
                            showWeiXinIdTutorialDialog = true
                        },
                        holdDownState = false
                    )
                }
            }
        }
    }
}
