package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class CheckCredentialsExistUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): Boolean {
        return preferencesRepository.hasCredentials()
    }
}