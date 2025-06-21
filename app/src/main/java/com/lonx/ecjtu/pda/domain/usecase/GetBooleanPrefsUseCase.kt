package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class GetBooleanPrefsUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(key: String): Boolean {
        return preferencesRepository.getBoolean(key, false)
    }
}