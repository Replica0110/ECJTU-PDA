package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.utils.PreferencesManager

class JwxtViewModel(
    private val service: JwxtService,
    private val prefs: PreferencesManager
):ViewModel() {
}