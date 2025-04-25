package com.lonx.ecjtu.pda.domain.repository

import com.lonx.ecjtu.pda.data.common.PDAResult

interface AuthRepository {
    /** Checks if the user currently has a valid session (e.g., necessary cookies). */
    suspend fun hasValidSession(): Boolean // Replaces hasLogin(1)

    /** Checks if the core CAS ticket exists. */
    suspend fun hasCasTicket(): Boolean // Replaces hasLogin(0)

    /** Attempts to log in using stored credentials. Handles session checks and redirects. */
    suspend fun login(forceRefresh: Boolean = false): PDAResult<Unit>

    /** Performs login with explicitly provided credentials and saves them on success. */
    suspend fun loginManually(studentId: String, studentPass: String, ispOption: Int): PDAResult<Unit>

    /** Logs the user out, clearing session and optionally stored credentials. */
    suspend fun logout(clearStoredCredentials: Boolean = true) // Make suspend if prefs clearing is async

    /** Checks if the current JWXT session is active via a network request. */
    suspend fun checkSessionValidity(): Boolean // Replaces checkSession()

    /** Updates the user's password on the platform. */
    suspend fun updatePassword(oldPassword: String, newPassword: String): PDAResult<String>

    // Optional: Expose login state reactively
    // fun isLoggedIn(): Flow<Boolean>
}