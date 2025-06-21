package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class UpdateStuCredentialsUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(studentId: String?=null, studentPass: String?=null, ispOption: Int?=null) {
        preferencesRepository.saveCredentials(studentId, studentPass, ispOption)
    }
}