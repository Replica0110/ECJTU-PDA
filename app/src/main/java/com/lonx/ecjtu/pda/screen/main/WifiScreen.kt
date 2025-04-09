package com.lonx.ecjtu.pda.screen.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.ui.ConfirmAlertDialog
import com.lonx.ecjtu.pda.ui.InfoAlertDialog
import com.lonx.ecjtu.pda.viewmodel.WifiUiEvent
import com.lonx.ecjtu.pda.viewmodel.WifiViewModel
import org.koin.compose.viewmodel.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WifiScreen(
    internalNavController: NavHostController,
    padding: PaddingValues,
//    scrollBehavior: UpdatableScrollBehavior,
    wifiViewModel: WifiViewModel = koinViewModel()
) {
    val uiState by wifiViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var dialogToShow by remember { mutableStateOf<DialogType?>(null) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }


//    val nestedScrollConnection = rememberAppBarNestedScrollConnection(
//        scrollBehavior = scrollBehavior
//    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            wifiViewModel.onPermissionResult(isGranted)
        }
    )
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wifiViewModel.refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(key1 = wifiViewModel) {
        wifiViewModel.uiEvent.collect { event ->
            Timber.tag("WifiScreen").d("Received UiEvent: $event")
            dialogToShow = null
            when (event) {
                is WifiUiEvent.NavigateToWifiSettings -> context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                is WifiUiEvent.NavigateToLocationSettings -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                is WifiUiEvent.NavigateToAppSettings -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
                is WifiUiEvent.RequestLocationPermission -> {
                    if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        wifiViewModel.showPermissionExplain()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
                is WifiUiEvent.ShowInfoDialog -> {
                    dialogTitle = event.title
                    dialogMessage = event.message
                    dialogToShow = DialogType.INFO
                }
                is WifiUiEvent.ShowLocationEnablePromptDialog -> {
                    dialogTitle = "需要开启位置信息"
                    dialogMessage = "应用需要您开启位置信息服务以获取WiFi信息，是否前往设置开启？"
                    dialogToShow = DialogType.LOCATION_PROMPT
                }
                is WifiUiEvent.ShowPermissionDeniedAppSettingsPromptDialog -> {
                    dialogTitle = "需要位置权限"
                    dialogMessage = "请在应用设置中手动授予位置权限以获取WiFi信息。"
                    dialogToShow = DialogType.PERMISSION_DENIED
                }
            }
        }
    }
    when (dialogToShow) {
        DialogType.INFO -> {
            InfoAlertDialog(
                showDialog = true,
                onDismissRequest = { dialogToShow = null },
                title = dialogTitle,
                message = buildAnnotatedString{ append(dialogMessage )}
            )
        }
        DialogType.LOCATION_PROMPT -> {
            ConfirmAlertDialog(
                showDialog = true,
                onDismissRequest = { dialogToShow = null },
                onConfirm = {
                    wifiViewModel.navigateToLocationSettings()
                    dialogToShow = null
                },
                title = dialogTitle,
                message = dialogMessage,
                confirmButtonText = "去设置",
                dismissButtonText = "取消"
            )
        }
        DialogType.PERMISSION_DENIED -> {
            ConfirmAlertDialog(
                showDialog = true,
                onDismissRequest = { dialogToShow = null },
                onConfirm = {
                    wifiViewModel.navigateToAppSettings()
                    dialogToShow = null // Hide dialog after action
                },
                title = dialogTitle,
                message = dialogMessage,
                confirmButtonText = "去设置",
                dismissButtonText = "取消"
            )
        }
        null -> {
        }
    }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = padding
        ) {
            item {
                Card(
                    modifier = Modifier
                        .padding(16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp)
                                .clickable(onClick = { wifiViewModel.onOpenWifiSettingsClicked() }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = uiState.wifiStatusIconRes),
                                contentDescription = "WiFi Status Icon",
                                modifier = Modifier.size(64.dp),
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            headlineContent = { Text("网络状态") },
                            trailingContent = {
                                Text(
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSecondaryVariant,
                                    text = uiState.wifiStatusText,
                                )
                            },
                            modifier = Modifier.clickable(onClick = { wifiViewModel.onOpenWifiSettingsClicked() }),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        ListItem(
                            headlineContent = { Text("SSID / 状态") },
                            trailingContent = {
                                Text(
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSecondaryVariant,
                                    text = uiState.ssid,
                                )
                            },
                            modifier = Modifier.clickable(onClick = { wifiViewModel.onCheckPermissionsClicked() }),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            Timber.d("Login Button Clicked")
                            wifiViewModel.onLoginClicked()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoadingIn && !uiState.isLoadingOut
                    ) {
                        if (uiState.isLoadingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("登录")
                        }
                    }

                    Button(
                        onClick = {
                            Timber.d("Logout Button Clicked")
                            wifiViewModel.onLogoutClicked()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoadingIn && !uiState.isLoadingOut
                    ) {
                        if (uiState.isLoadingOut) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("注销")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

        }

}
private enum class DialogType {
    INFO,
    LOCATION_PROMPT,
    PERMISSION_DENIED
}