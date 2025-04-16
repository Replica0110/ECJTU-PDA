package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class GetWeiXinIDUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): String {
        return preferencesRepository.getWeiXinId()
    }
}