package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.model.CampusNetStatus
import com.lonx.ecjtu.pda.domain.repository.WifiRepository

class GetCampusNetStatusUseCase(
    private val wifiRepository: WifiRepository
) {
    suspend operator fun invoke(): CampusNetStatus {
        return wifiRepository.getStatus()
    }
}