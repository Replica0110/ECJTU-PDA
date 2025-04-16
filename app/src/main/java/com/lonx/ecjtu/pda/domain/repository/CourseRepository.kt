package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.ServiceResult
import com.lonx.ecjtu.pda.data.model.StuDayCourses

interface CourseRepository {
    suspend fun getCoursesForDate(dateQuery: String?): ServiceResult<StuDayCourses>

}