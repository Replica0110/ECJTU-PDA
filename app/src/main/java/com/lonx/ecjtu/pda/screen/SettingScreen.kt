package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.IspOption
import com.lonx.ecjtu.pda.data.availableIsp
import com.lonx.ecjtu.pda.ui.AlertDialogContainer
import com.lonx.ecjtu.pda.ui.CustomDropdownMenu
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.utils.rememberAppBarNestedScrollConnection
import com.lonx.ecjtu.pda.viewmodel.SettingUiEvent
import com.lonx.ecjtu.pda.viewmodel.SettingViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Personal
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingScreen(
    padding: PaddingValues,
    scrollBehavior: UpdatableScrollBehavior,
    settingViewModel: SettingViewModel = koinViewModel()
) {
    val uiState by settingViewModel.uiState.collectAsState()
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by rememberSaveable  { mutableStateOf(false) }
    val nestedScrollConnection = rememberAppBarNestedScrollConnection(scrollBehavior)
    // --- State for Snackbar Colors ---
    var snackbarContainerColor by remember { mutableStateOf(Color.Unspecified) }
    var snackbarContentColor by remember { mutableStateOf(Color.Unspecified) }

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
        initialOldPassword = "",
        confirmButtonText = "确认",
        dismissButtonText = "取消",
        isLoading = uiState.isLoading
    )
    val snackbarHostState = remember { SnackbarHostState() }

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
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
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
                }

            }
        }
    }
}
@Composable
fun AccountConfigDialog(
    show: Boolean,
    currentStudentId: String,
    currentPassword: String,
    currentIsp: IspOption,
    isLoading: Boolean,
    error: String?,
    onDismissRequest: () -> Unit,
    onSave: (String, String, IspOption) -> Unit,
    modifier: Modifier = Modifier
) {

    var tempStudentId by rememberSaveable { mutableStateOf(currentStudentId) }
    var tempPassword by rememberSaveable { mutableStateOf(currentPassword) }
    var tempSelectedIsp by rememberSaveable { mutableStateOf(currentIsp) }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var internalError by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialogContainer(
        showDialog = show,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = "账号配置",
        confirmButtonText = "保存",
        onConfirm = {
            internalError = null
            if (tempPassword.isBlank()) {
                internalError = "新密码不能为空"
                return@AlertDialogContainer
            }
            onSave(tempStudentId, tempPassword, tempSelectedIsp)
            if (internalError == null) {
                onDismissRequest()
            }
        },
        dismissButtonText = "取消",
        onDismissAction = { onDismissRequest() },
        isLoading = isLoading
    ) {

        TextField(
            label = "学号",
            value = tempStudentId,
            onValueChange = { tempStudentId = it },
            singleLine = true,
            leadingIcon = { Icon( Icons.Default.Person, contentDescription = "学号图标", modifier = Modifier.padding(horizontal = 4.dp)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            label = "密码",
            value = tempPassword,
            onValueChange = { tempPassword = it; internalError = null },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "密码图标", modifier = Modifier.padding(horizontal = 4.dp)) },
            trailingIcon = {
                val image = if (isPasswordVisible)
                    painterResource(id = R.drawable.ic_visible)
                else painterResource(id = R.drawable.ic_invisible)

                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(image, contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        CustomDropdownMenu(
            label = "运营商",
            options = availableIsp,
            leadingIcon = { Icon(Icons.Default.Call, contentDescription = "运营商图标", modifier = Modifier.padding(horizontal = 4.dp))},
            selectedOption = tempSelectedIsp,
            onOptionSelected = { selectedIsp -> tempSelectedIsp = selectedIsp },
            optionToText = { ispOption -> ispOption.name },
            modifier = Modifier.fillMaxWidth()
        )

        if (internalError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = internalError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
@Composable
fun ChangePasswordDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (old: String, new: String) -> Unit,
    initialOldPassword: String = "",
    confirmButtonText: String = "确认",
    dismissButtonText: String = "取消",
    isLoading: Boolean = false
) {
    var oldPassword by rememberSaveable { mutableStateOf(initialOldPassword) }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isOldPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isNewPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var internalError by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    AlertDialogContainer(
        showDialog = showDialog,
        onDismissRequest = onDismissRequest,
        title = "修改密码",
        confirmButtonText = confirmButtonText,
        onConfirm = {
            internalError = null
            if (newPassword.isBlank()) {
                internalError = "新密码不能为空"
                return@AlertDialogContainer
            }
            if (newPassword != confirmPassword) {
                internalError = "两次输入的新密码不一致"
                return@AlertDialogContainer
            }
            focusManager.clearFocus()
            onConfirm(oldPassword, newPassword)
            if (internalError == null) {
                oldPassword = ""
                newPassword = ""
                confirmPassword = ""
                onDismissRequest()
            }
        },
        dismissButtonText = dismissButtonText,
        onDismissAction = { onDismissRequest() },
        isLoading = isLoading
    ) {
        TextField(
            value = oldPassword,
            onValueChange = { oldPassword = it; internalError = null },
            label = "当前密码",
            singleLine = true,
            leadingIcon = {
                androidx.compose.material3.Icon(
                    Icons.Default.Lock,
                    contentDescription = "密码图标",
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            },
            visualTransformation = if (isOldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            trailingIcon = {
                PasswordVisibilityToggle(isVisible = isOldPasswordVisible) {
                    isOldPasswordVisible = !isOldPasswordVisible
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)

        )

        TextField(
            value = newPassword,
            onValueChange = { newPassword = it; internalError = null },
            label = "新密码",
            singleLine = true,
            leadingIcon = {
                androidx.compose.material3.Icon(
                    Icons.Default.Lock,
                    contentDescription = "密码图标",
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            },
            visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            trailingIcon = {
                PasswordVisibilityToggle(isVisible = isNewPasswordVisible) {
                    isNewPasswordVisible = !isNewPasswordVisible
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )

        TextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; internalError = null },
            label = "确认新密码",
            singleLine = true,
            leadingIcon = {
                androidx.compose.material3.Icon(
                    Icons.Default.Lock,
                    contentDescription = "密码图标",
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            },
            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            trailingIcon = {
                PasswordVisibilityToggle(isVisible = isConfirmPasswordVisible) {
                    isConfirmPasswordVisible = !isConfirmPasswordVisible
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        if (internalError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = internalError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PasswordVisibilityToggle(
    isVisible: Boolean,
    onToggle: () -> Unit
) {
    val image = if (isVisible) painterResource(id = R.drawable.ic_visible) else painterResource(id = R.drawable.ic_invisible) // Use M3 icons
    val description = if (isVisible) "隐藏密码" else "显示密码"

    IconButton(onClick = onToggle) {
        Icon(painter = image, contentDescription = description)
    }
}