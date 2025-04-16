package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class GetStuCredentialsUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): Triple<String, String, Int> {
        return preferencesRepository.getCredentials()
    }
}