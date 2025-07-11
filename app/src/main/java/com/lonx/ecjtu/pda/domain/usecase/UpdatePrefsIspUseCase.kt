package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class UpdatePrefsIspUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(ispOption: Int) {
        preferencesRepository.saveCredentials(ispOption = ispOption)
    }
}