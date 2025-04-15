package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.data.TermSchedules
import com.lonx.ecjtu.pda.service.StuScheduleService


class StuScheduleViewModel(
    private val service: StuScheduleService
) : BaseResultViewModel<List<TermSchedules>>(
    fetchData = { service.getAllSchedules() }
)
