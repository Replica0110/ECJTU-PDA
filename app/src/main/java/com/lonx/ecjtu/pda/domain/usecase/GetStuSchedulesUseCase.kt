package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.model.StuAllSchedules
import com.lonx.ecjtu.pda.domain.repository.SchedulesRepository

class GetStuSchedulesUseCase(
    private val schedulesRepository: SchedulesRepository
) {
    suspend operator fun invoke(): ServiceResult<StuAllSchedules> {
        return schedulesRepository.getAllSchedules()
    }
}