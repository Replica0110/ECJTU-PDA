package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.data.model.StuAllSchedules
import com.lonx.ecjtu.pda.domain.usecase.GetStuSchedulesUseCase


class StuScheduleViewModel(
    private val getSchedulesUseCase: GetStuSchedulesUseCase
) : BaseResultViewModel<StuAllSchedules>(
    fetchData = { getSchedulesUseCase() }
)
