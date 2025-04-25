package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.StuAllElectiveCourses

interface ElectiveRepository {
    suspend fun getStudentElectiveCourse(): PDAResult<StuAllElectiveCourses>
}