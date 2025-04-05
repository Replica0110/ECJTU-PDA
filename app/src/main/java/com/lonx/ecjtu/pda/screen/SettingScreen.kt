package com.lonx.ecjtu.pda.screen

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.IspOption
import com.lonx.ecjtu.pda.data.availableIsp
import com.lonx.ecjtu.pda.ui.CustomDropdownMenu
import com.lonx.ecjtu.pda.utils.UpdatableScrollBehavior
import com.lonx.ecjtu.pda.viewmodel.SettingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LazyColumn
//import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Personal
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape

@Composable
fun SettingScreen(
    padding: PaddingValues,
    scrollBehavior: UpdatableScrollBehavior,
    settingViewModel: SettingViewModel = koinViewModel()
) {
    val uiState by settingViewModel.uiState.collectAsState()
    val showConfigDialog = remember { mutableStateOf(false) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0) {
                    val consumedY = scrollBehavior.updateHeightOffset(available.y)
                    if (consumedY != 0f) {
                        return Offset(0f, consumedY)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    scrollBehavior.updateHeightOffset(available.y)
                }
                return Offset.Zero
            }

        }
    }
    if (showConfigDialog.value) {
        AccountConfigDialog(
            currentStudentId = uiState.studentId,
            currentPassword = uiState.password,
            currentIsp = uiState.ispSelected,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onDismiss = { showConfigDialog.value = false },
            onSave = { studentId, password, selectedIsp ->
                settingViewModel.updateConfig(studentId, password, selectedIsp)
            }
        )
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        contentPadding = padding
    ){

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
                        showConfigDialog.value = true
                    },
                    holdDownState = false
                )
            }

        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountConfigDialog(
    currentStudentId: String,
    currentPassword: String,
    currentIsp: IspOption,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, IspOption) -> Unit
) {
    var tempStudentId by remember { mutableStateOf(currentStudentId) }
    var tempPassword by remember { mutableStateOf(currentPassword) }
    var tempSelectedIsp by remember { mutableStateOf(currentIsp) }
    var isPasswordVisible by remember { mutableStateOf(false) }


    LaunchedEffect(currentStudentId, currentPassword, currentIsp) {
        tempStudentId = currentStudentId
        tempPassword = currentPassword
        tempSelectedIsp = currentIsp
    }


    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp)
        ) {

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("账号配置", style = MiuixTheme.textStyles.title3, textAlign = TextAlign.Center, modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    label = "学号",
                    value = tempStudentId,
                    onValueChange = { tempStudentId = it },
                    singleLine = true,

                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next) // Adjust if needed
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    label = "密码",
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                    selectedOption = tempSelectedIsp,
                    onOptionSelected = { selectedIsp ->
                        tempSelectedIsp = selectedIsp
                    },
                    optionToText = { ispOption -> ispOption.name },
                    modifier = Modifier.fillMaxWidth()
                )

                 
                
                Spacer(modifier = Modifier.height(16.dp))

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(
                        onClick = { onDismiss() },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        text = "取消")
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        text = "保存",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSave(tempStudentId, tempPassword, tempSelectedIsp)
                            onDismiss()
                                  },
                        enabled = !isLoading
                    )
                }
            }
        }
    }
}
