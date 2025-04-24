package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.model.StuSecondCredits

interface SecondCreditRepository {
    suspend fun getSecondCredit(): ServiceResult<StuSecondCredits>
}