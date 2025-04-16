package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.data.model.StuAllElectiveCourses
import com.lonx.ecjtu.pda.domain.usecase.GetStuElectiveUseCase


class StuElectiveViewModel(
    private val getStuElectiveUseCase: GetStuElectiveUseCase
) : BaseResultViewModel<StuAllElectiveCourses>(
    fetchData = { getStuElectiveUseCase() }
)