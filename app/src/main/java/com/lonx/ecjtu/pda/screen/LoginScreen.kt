package com.lonx.ecjtu.pda.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
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
import com.lonx.ecjtu.pda.data.AppRoutes
import com.lonx.ecjtu.pda.data.NavigationTarget
import com.lonx.ecjtu.pda.viewmodel.LoginViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavHostController,
    loginViewModel: LoginViewModel = koinViewModel(), // Use Koin or your DI
    onLoginSuccess: () -> Unit
) {
    val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    var ispDropdownExpanded by remember { mutableStateOf(false) }

    val selectedIspName = remember(uiState.selectedIspId, uiState.ispOptions) {
        uiState.ispOptions.find { it.id == uiState.selectedIspId }?.name ?: "选择运营商" // Fallback text
    }

    LaunchedEffect(uiState.navigationEvent) {
        if (uiState.navigationEvent == NavigationTarget.MAIN) {
            onLoginSuccess()
            navController.navigate(AppRoutes.MAIN) {
                popUpTo(AppRoutes.LOGIN) { inclusive = true }
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
        containerColor = MaterialTheme.colorScheme.background
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

            OutlinedTextField(
                value = uiState.studentId,
                onValueChange = { loginViewModel.onStudentIdChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("学号") },
                placeholder = { Text("请输入学号") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "学号图标") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text, // Changed to Text, can be Numbers if needed
                    imeAction = ImeAction.Next // Move to next field
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                isError = uiState.error != null // Indicate error on both fields
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- Password Input ---
            OutlinedTextField(
                value = uiState.password,
                onValueChange = { loginViewModel.onPasswordChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                placeholder = { Text("请输入密码") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "密码图标") },
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
                    imeAction = ImeAction.Next // Change to Next to move to ISP selection
                ),
                keyboardActions = KeyboardActions(
                    // Move focus to the dropdown (or Login button if dropdown not focused)
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                isError = uiState.error != null // Indicate error on both fields
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- ISP Selection Dropdown ---
            ExposedDropdownMenuBox(
                expanded = ispDropdownExpanded,
                onExpandedChange = { ispDropdownExpanded = !ispDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                // The TextField part of the dropdown
                OutlinedTextField(
                    value = selectedIspName, // Display the selected ISP's name
                    onValueChange = {}, // Value change is handled by DropdownMenuItem click
                    readOnly = true, // Make it non-editable
                    label = { Text("运营商") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = ispDropdownExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        // Match text field colors or use defaults
                        // containerColor = ..., focusedBorderColor = ..., etc.
                    ),
                    modifier = Modifier
                        .menuAnchor() // Important anchor for the dropdown
                        .fillMaxWidth()
                )

                // The actual dropdown menu
                ExposedDropdownMenu(
                    expanded = ispDropdownExpanded,
                    onDismissRequest = { ispDropdownExpanded = false } // Close when clicking outside
                ) {
                    uiState.ispOptions.forEach { isp ->
                        DropdownMenuItem(
                            text = { Text(isp.name) },
                            onClick = {
                                loginViewModel.onIspSelected(isp.id) // Update ViewModel state
                                ispDropdownExpanded = false // Close the dropdown
                                // Optionally move focus to login button or clear focus
                                focusManager.moveFocus(FocusDirection.Down) // Move to Login button
                            }
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(32.dp))

            // --- Login Button ---
            Button( // Using standard Button, replace with ItemButton if needed
                onClick = {
                    focusManager.clearFocus() // Hide keyboard
                    loginViewModel.attemptLogin()
                },
                enabled = !uiState.isLoading, // Disable button when loading
                modifier = Modifier.fillMaxWidth().height(48.dp) // Ensure good button size
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary, // Color for indicator on button
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("登录")
                }
            }

            Spacer(modifier = Modifier.height(24.dp)) // Space before the hint text

            // --- Hint Text ---
            Text(
                text = "运营商选择用于校园网登录认证，如不清楚或不需要可忽略（将使用默认）。",
                style = MaterialTheme.typography.bodySmall, // Smaller text style
                color = MaterialTheme.colorScheme.onSurfaceVariant // Less prominent color
            )

            Spacer(modifier = Modifier.height(16.dp)) // Bottom padding within the Column

        } // End Main Column
    }
}