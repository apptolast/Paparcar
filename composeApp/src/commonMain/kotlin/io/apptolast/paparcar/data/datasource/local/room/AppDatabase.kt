package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LocationEntity::class, ParkingSessionEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun parkingSessionDao(): ParkingSessionDao
}
