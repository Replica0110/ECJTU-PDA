package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.domain.repository.ProfileRepository

class GetStuProfileUseCase(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(): PDAResult<Map<String, Map<String, String>>> {
        return profileRepository.getStudentProfile()
    }
}