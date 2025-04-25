package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.StuAllScores

interface ScoreRepository {
    suspend fun getStudentScores(): PDAResult<StuAllScores>
}