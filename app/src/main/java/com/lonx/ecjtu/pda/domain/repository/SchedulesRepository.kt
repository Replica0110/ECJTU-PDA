package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.StuAllSchedules

interface SchedulesRepository {

    suspend fun getAllSchedules(): PDAResult<StuAllSchedules>
}