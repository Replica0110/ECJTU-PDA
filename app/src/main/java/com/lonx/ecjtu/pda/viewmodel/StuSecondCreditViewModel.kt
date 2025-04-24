package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.data.model.StuSecondCredits
import com.lonx.ecjtu.pda.domain.usecase.GetStuSecondCreditUseCase

class StuSecondCreditViewModel(
    private val getStuSecondCreditUseCase: GetStuSecondCreditUseCase
) :BaseResultViewModel<StuSecondCredits>(
    fetchData = { getStuSecondCreditUseCase() }
)