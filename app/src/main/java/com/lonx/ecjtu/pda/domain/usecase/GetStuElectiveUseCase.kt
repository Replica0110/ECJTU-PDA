package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.StuAllElectiveCourses
import com.lonx.ecjtu.pda.domain.repository.ElectiveRepository

class GetStuElectiveUseCase(
    private val electiveRepository: ElectiveRepository
) {
    suspend operator fun invoke(): PDAResult<StuAllElectiveCourses> {
        return electiveRepository.getStudentElectiveCourse()
    }
}