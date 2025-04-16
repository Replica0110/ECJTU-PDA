package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.domain.repository.ProfileRepository

class GetStuProfileUseCase(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(): ServiceResult<Map<String, Map<String, String>>> {
        return profileRepository.getStudentProfile()
    }
}