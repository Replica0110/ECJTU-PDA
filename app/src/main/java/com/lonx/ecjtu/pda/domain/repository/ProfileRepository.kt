package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.ServiceResult

interface ProfileRepository {
    /**
     * 获取学生档案信息，按类别分组。
     * @return ServiceResult 包含按类别分组的信息 Map (e.g., "基本信息" -> Map<String, String>)。
     */
    suspend fun getStudentProfile(): ServiceResult<Map<String, Map<String, String>>>
}