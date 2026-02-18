package io.apptolast.paparcar.domain.repository

interface SessionRepository {
    suspend fun ensureUserId(): String
}
