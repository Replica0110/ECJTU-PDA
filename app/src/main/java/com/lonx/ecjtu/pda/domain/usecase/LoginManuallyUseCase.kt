package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.domain.repository.AuthRepository

class LoginManuallyUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        studentId: String,
        studentPass: String,
        ispOption: Int
    ): PDAResult<Unit> {
        if (studentId.isBlank() || studentPass.isBlank()) {
             return PDAResult.Error("账号或密码不能为空")
        }
        return authRepository.loginManually(studentId.trim(), studentPass, ispOption)
    }
}