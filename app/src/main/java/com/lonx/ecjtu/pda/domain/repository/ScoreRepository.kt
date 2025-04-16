package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.model.StuAllScores

interface ScoreRepository {
    suspend fun getStudentScores(): ServiceResult<StuAllScores>
}