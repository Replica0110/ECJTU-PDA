package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.domain.repository.AuthRepository

class LoginUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): PDAResult<Unit> {
        return authRepository.login()
    }
}