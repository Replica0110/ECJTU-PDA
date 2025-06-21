package com.lonx.ecjtu.pda.screen.jwxt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.ecjtu.pda.data.model.TermExperiments
import com.lonx.ecjtu.pda.state.UiState
import com.lonx.ecjtu.pda.viewmodel.StuExperimentViewModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StuExperimentScreen(
    onBack: () -> Unit,
    viewModel: StuExperimentViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val semesterExperiments = (uiState as? UiState.Success)?.data?.experiments

    LaunchedEffect(semesterExperiments) {
        selectedIndex = if (semesterExperiments != null) {
            when {
                selectedIndex >= semesterExperiments.size && semesterExperiments.isNotEmpty() -> semesterExperiments.size - 1
                semesterExperiments.isEmpty() -> 0
                selectedIndex < 0 -> 0
                else -> selectedIndex
            }
        } else  {
            0
        }
    }
    LaunchedEffect(Unit) {
        if (uiState !is UiState.Success && uiState !is UiState.Loading && uiState !is UiState.Error) {
            viewModel.load()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "如果加载时间较长，可能登录已过期，应用正在重新登录",
                            color = Color.Gray,
                            style = MiuixTheme.textStyles.subtitle,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 30.dp)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                semesterExperiments?.forEachIndexed { index, termExperiments ->
                    item {
                        ExperimentTermItem(
                            termExperiments = termExperiments,
                            isSelected = index == selectedIndex,
                            onClick = {
                                selectedIndex = index
                            }
                        )
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Sharp.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error

                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {}
        }
    }
}


@Composable
fun ExperimentTermItem(termExperiments: TermExperiments, isSelected: Boolean, onClick: () -> Unit) {

}
