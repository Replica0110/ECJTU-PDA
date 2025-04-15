package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.service.StuElectiveService
import com.lonx.ecjtu.pda.service.StudentElectiveCourses


class StuElectiveViewModel(
    private val service: StuElectiveService
) : BaseResultViewModel<StudentElectiveCourses>(
    fetchData = { service.getElectiveCourses() }
)