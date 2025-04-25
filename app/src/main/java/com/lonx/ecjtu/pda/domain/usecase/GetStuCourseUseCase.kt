package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.StuDayCourses
import com.lonx.ecjtu.pda.domain.repository.CourseRepository

class GetStuCourseUseCase(
    private val courseRepository: CourseRepository
) {
    suspend operator fun invoke(dateQuery: String? = null): PDAResult<StuDayCourses> {
        return courseRepository.getCoursesForDate(dateQuery)
    }
}