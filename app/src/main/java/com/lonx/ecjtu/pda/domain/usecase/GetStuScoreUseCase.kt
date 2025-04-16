package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.model.StuAllScores
import com.lonx.ecjtu.pda.domain.repository.ScoreRepository

class GetStuScoreUseCase(
    private val scoreRepository: ScoreRepository
) {
    suspend operator fun invoke(): ServiceResult<StuAllScores> {
        return scoreRepository.getStudentScores()
    }
}