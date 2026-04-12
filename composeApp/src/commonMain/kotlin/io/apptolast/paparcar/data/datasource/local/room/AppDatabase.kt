package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [UserParkingEntity::class, UserProfileEntity::class, VehicleEntity::class, SpotEntity::class],
    version = 8,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun parkingSessionDao(): UserParkingDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun spotDao(): SpotDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>