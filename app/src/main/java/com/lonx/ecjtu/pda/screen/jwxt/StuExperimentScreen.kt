package com.lonx.ecjtu.pda.screen.jwxt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.ecjtu.pda.viewmodel.StuExperimentViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun StuExperimentScreen(
    onBack: () -> Unit,
    viewModel: StuExperimentViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState.experiments == null && !uiState.isLoading) {
            viewModel.loadExperiments()
        }
    }
}