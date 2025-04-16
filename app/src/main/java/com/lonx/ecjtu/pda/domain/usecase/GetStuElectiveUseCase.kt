package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.model.StuAllElectiveCourses
import com.lonx.ecjtu.pda.domain.repository.ElectiveRepository

class GetStuElectiveUseCase(
    private val electiveRepository: ElectiveRepository
) {
    suspend operator fun invoke(): ServiceResult<StuAllElectiveCourses> {
        return electiveRepository.getStudentElectiveCourse()
    }
}