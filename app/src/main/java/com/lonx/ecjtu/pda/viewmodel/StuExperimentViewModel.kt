package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.data.model.StuAllExperiments
import com.lonx.ecjtu.pda.domain.usecase.GetStuExperimentsUseCase

class StuExperimentViewModel(
    private val getStuExperimentsUseCase: GetStuExperimentsUseCase
): BaseResultViewModel<StuAllExperiments>(
    fetchData = { getStuExperimentsUseCase() }
)
