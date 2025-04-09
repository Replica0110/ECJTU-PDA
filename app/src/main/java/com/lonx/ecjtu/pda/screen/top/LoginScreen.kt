package com.lonx.ecjtu.pda.screen.top

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.R
import com.lonx.ecjtu.pda.data.NavigationTarget
import com.lonx.ecjtu.pda.data.TopLevelRoute
import com.lonx.ecjtu.pda.ui.CustomDropdownMenu
import com.lonx.ecjtu.pda.viewmodel.LoginViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoginScreen(
    navController: NavHostController,
    loginViewModel: LoginViewModel = koinViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.navigationEvent) {
        if (uiState.navigationEvent == NavigationTarget.MAIN) {
            onLoginSuccess()
            navController.navigate(TopLevelRoute.Main.route) {
                popUpTo(TopLevelRoute.Login.route) { inclusive = true }
                launchSingleTop = true
            }
            loginViewModel.onNavigationHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            focusManager.clearFocus()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            loginViewModel.onErrorMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MiuixTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            TextField(
                value = uiState.studentId,
                onValueChange = { loginViewModel.onStudentIdChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = "学号",
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "学号图标", modifier = Modifier.padding(horizontal = 4.dp)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = uiState.password,
                onValueChange = { loginViewModel.onPasswordChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = "密码",
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "密码图标", modifier = Modifier.padding(horizontal = 4.dp))},
                trailingIcon = {
                    val image = if (passwordVisible)
                        painterResource(id = R.drawable.ic_visible)
                    else painterResource(id = R.drawable.ic_invisible)
                    val description = if (passwordVisible) "隐藏密码" else "显示密码"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(image, description)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val selectedIspObject = remember(uiState.selectedIspId, uiState.ispOptions) {
                uiState.ispOptions.find { it.id == uiState.selectedIspId }
            }

            if (selectedIspObject != null && uiState.ispOptions.isNotEmpty()) {
                CustomDropdownMenu(
                    label = "运营商",
                    options = uiState.ispOptions,
                    leadingIcon = { Icon(Icons.Default.Call, contentDescription = "运营商图标", modifier = Modifier.padding(horizontal = 4.dp))},
                    selectedOption = selectedIspObject,
                    onOptionSelected = { selectedIsp ->
                        loginViewModel.onIspSelected(selectedIsp.id)
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    optionToText = { isp -> isp.name }
                )
            } else {
                TextField(
                    value = if (uiState.ispOptions.isEmpty()) "加载中..." else "选择运营商",
                    onValueChange = {},
                    label = "运营商",
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }


            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    loginViewModel.attemptLogin()
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("登录")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "运营商选择用于校园网登录认证，如不清楚或不需要可忽略。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

        }
    }
}