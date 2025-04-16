package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class UpdateStuCredentialsUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(studentId: String, studentPass: String, ispOption: Int) {
        preferencesRepository.saveCredentials(studentId, studentPass, ispOption)
    }
}