package com.lonx.ecjtu.pda.domain.source

import com.lonx.ecjtu.pda.data.common.ServiceResult

interface JwxtApiClient {
    /** Fetches the HTML content for the student scores page. */
    suspend fun getStudentScoresHtml(): ServiceResult<String>

    /** Fetches the HTML content for the second credit page. */
    suspend fun getSecondCreditHtml(): ServiceResult<String>

    /** Fetches the HTML content for the student profile page. */
    suspend fun getProfileHtml(): ServiceResult<String>

    /** Fetches the HTML content for the daily course schedule. */
    suspend fun getCourseScheduleHtml(dateQuery: String? = null): ServiceResult<String>

    /** Fetches the HTML content for the term-based schedule. */
    suspend fun getScheduleHtml(term: String? = null): ServiceResult<String>

    /** Fetches the HTML content for the elective courses page. */
    suspend fun getElectiveCourseHtml(term: String? = null): ServiceResult<String>

    /** Fetches the HTML content for the experiments page. */
    suspend fun getExperimentsHtml(term: String? = null): ServiceResult<String>

    /** Fetches the YKT (One Card) balance. */
    suspend fun getYktBalance(): ServiceResult<String> // Renamed from getYktNum for clarity

    // Maybe add a generic fetcher if needed, but specific methods are often clearer
    // suspend fun fetchGenericHtml(url: HttpUrl, referer: String?, params: Map<String, String>?): ServiceResult<String>
}