package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.data.StudentScoresData
import com.lonx.ecjtu.pda.service.StuScoreService


class StuScoreViewModel(
    val service: StuScoreService
): BaseResultViewModel<StudentScoresData>(
    fetchData = { service.getStudentScores() }
)