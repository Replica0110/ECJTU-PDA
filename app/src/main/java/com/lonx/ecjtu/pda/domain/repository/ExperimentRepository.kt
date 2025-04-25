package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.StuAllExperiments

interface ExperimentRepository {
    suspend fun getAllExperiments(): PDAResult<StuAllExperiments>
}