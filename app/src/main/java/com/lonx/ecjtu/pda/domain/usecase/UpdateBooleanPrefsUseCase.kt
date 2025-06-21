package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class UpdateBooleanPrefsUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(key: String, value: Boolean) {
        preferencesRepository.setBoolean(key, value)
    }
}