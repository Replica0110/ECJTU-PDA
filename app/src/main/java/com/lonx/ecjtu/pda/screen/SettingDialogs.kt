package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.common.IspOption
import com.lonx.ecjtu.pda.data.common.availableIsp
import com.lonx.ecjtu.pda.ui.component.CustomDropdownMenu
import com.lonx.ecjtu.pda.ui.dialog.AlertDialogContainer
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

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
            leadingIcon = { Icon(Icons.Default.Call, contentDescription = "运营商图标", modifier = Modifier.padding(horizontal = 4.dp)) },
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