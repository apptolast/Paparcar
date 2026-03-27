package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
