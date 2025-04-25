package com.lonx.ecjtu.pda.domain.usecase

import android.content.Context
import com.lonx.ecjtu.pda.common.NetworkType
import com.lonx.ecjtu.pda.domain.repository.WifiRepository

class GetNetworkTypeUseCase(
    private val wifiRepository: WifiRepository
) {
    suspend operator fun invoke(context: Context): NetworkType {
        return wifiRepository.getNetworkType(context = context)
    }
}