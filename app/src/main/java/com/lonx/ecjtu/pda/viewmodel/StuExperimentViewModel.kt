package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.service.ExperimentData
import com.lonx.ecjtu.pda.service.StuExperimentService

class StuExperimentViewModel(
    private val service: StuExperimentService
): BaseResultViewModel<List<ExperimentData>>(
    fetchData = { service.getExperiments() }
)
