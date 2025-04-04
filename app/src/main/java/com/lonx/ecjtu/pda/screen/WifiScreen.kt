package com.lonx.ecjtu.pda.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.viewmodel.UiEvent
import com.lonx.ecjtu.pda.viewmodel.WifiViewModel
import org.koin.compose.viewmodel.koinViewModel

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WifiScreen(
    navHostController: NavHostController,
    wifiViewModel: WifiViewModel = koinViewModel()
) {
    val uiState by wifiViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var showInfoDialog by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf("") }
    var infoDialogMessage by remember { mutableStateOf("") }

    var showLocationEnablePromptDialog by remember { mutableStateOf(false) }

    var showPermissionDeniedDialog by remember { mutableStateOf(false) }



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
            when (event) {
                is UiEvent.NavigateToWifiSettings -> {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    context.startActivity(intent)
                }
                is UiEvent.NavigateToLocationSettings -> {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    context.startActivity(intent)
                    showLocationEnablePromptDialog = false
                }
                is UiEvent.NavigateToAppSettings -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                    showPermissionDeniedDialog = false
                }
                is UiEvent.RequestLocationPermission -> {
                    if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        wifiViewModel.showPermissionExplain()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
                is UiEvent.ShowInfoDialog -> {
                    infoDialogTitle = event.title
                    infoDialogMessage = event.message
                    showInfoDialog = true
                    showLocationEnablePromptDialog = false
                    showPermissionDeniedDialog = false
                }
                is UiEvent.ShowLocationEnablePromptDialog -> {
                    showLocationEnablePromptDialog = true
                    showInfoDialog = false
                    showPermissionDeniedDialog = false
                }
                is UiEvent.ShowPermissionDeniedAppSettingsPromptDialog -> {
                    showPermissionDeniedDialog = true
                    showInfoDialog = false
                    showLocationEnablePromptDialog = false
                }
            }
        }
    }
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(text = infoDialogTitle) },
            text = { Text(text = infoDialogMessage) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    if (showLocationEnablePromptDialog) {
        AlertDialog(
            onDismissRequest = { showLocationEnablePromptDialog = false },
            title = { Text("需要开启位置信息") },
            text = { Text("应用需要您开启位置信息服务以获取WiFi信息，是否前往设置开启？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        wifiViewModel.userConfirmedNavigateToLocationSettings()
                        showLocationEnablePromptDialog = false
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationEnablePromptDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("需要位置权限") },
            text = { Text("请在应用设置中手动授予位置权限以获取WiFi信息。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        wifiViewModel.userConfirmedNavigateToAppSettings()
                        showPermissionDeniedDialog = false
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = { Text("网络状态") },
                        trailingContent = {
                            Text(
                                text = uiState.wifiStatusText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                text = uiState.ssid,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable(onClick = { wifiViewModel.onCheckPermissionsClicked() }),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { wifiViewModel.onLoginClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoadingIn && !uiState.isLoadingOut
                ) {
                    if (uiState.isLoadingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("登录")
                    }
                }

                OutlinedButton(
                    onClick = { wifiViewModel.onLogoutClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoadingIn && !uiState.isLoadingOut
                ) {
                    if (uiState.isLoadingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
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
