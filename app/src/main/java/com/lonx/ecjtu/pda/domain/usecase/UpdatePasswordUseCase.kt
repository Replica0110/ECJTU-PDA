package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.domain.repository.AuthRepository

class UpdatePasswordUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): ServiceResult<String> {
        return authRepository.updatePassword(oldPassword, newPassword)
    }
}
