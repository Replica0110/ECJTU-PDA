package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.model.StuAllExperiments
import com.lonx.ecjtu.pda.domain.repository.ExperimentRepository

class GetStuExperimentsUseCase(
    private val experimentRepository: ExperimentRepository
) {
    suspend operator fun invoke(): ServiceResult<StuAllExperiments> {
        return experimentRepository.getAllExperiments()
    }
}