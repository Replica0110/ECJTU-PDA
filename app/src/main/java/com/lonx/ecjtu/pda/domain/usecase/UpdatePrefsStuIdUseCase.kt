package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class UpdatePrefsStuIdUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(studentId: String) {
        preferencesRepository.saveCredentials(studentId = studentId)
    }
}