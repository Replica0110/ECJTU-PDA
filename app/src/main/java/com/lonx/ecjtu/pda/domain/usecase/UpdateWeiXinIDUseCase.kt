package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class UpdateWeiXinIDUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(weixinid: String) {
        preferencesRepository.setWeiXinId(weixinid)
    }
}