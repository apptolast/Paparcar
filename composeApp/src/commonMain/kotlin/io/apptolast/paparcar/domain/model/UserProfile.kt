package io.apptolast.paparcar.domain.model

data class UserProfile(
    val userId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
