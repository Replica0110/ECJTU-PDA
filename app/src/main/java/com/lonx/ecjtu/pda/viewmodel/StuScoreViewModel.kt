package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.data.model.StuAllScores
import com.lonx.ecjtu.pda.domain.usecase.GetStuScoreUseCase


class StuScoreViewModel(
    val getStuScoreUseCase: GetStuScoreUseCase
): BaseResultViewModel<StuAllScores>(
    fetchData = { getStuScoreUseCase() }
)