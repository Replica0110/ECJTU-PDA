package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.domain.repository.WifiRepository

class CampusNetLoginUseCase(
    private val wifiRepository: WifiRepository
) {

    suspend operator fun invoke(): PDAResult<String> {
        return wifiRepository.login()
    }
}