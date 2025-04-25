package com.lonx.ecjtu.pda.domain.repository

import android.content.Context
import com.lonx.ecjtu.pda.common.NetworkType
import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.CampusNetStatus

interface WifiRepository {
    suspend fun getNetworkType(context: Context): NetworkType
    suspend fun login(): PDAResult<String>
    suspend fun logout(clearStoredCredentials: Boolean = true):PDAResult<String>
    suspend fun getStatus(): CampusNetStatus
}