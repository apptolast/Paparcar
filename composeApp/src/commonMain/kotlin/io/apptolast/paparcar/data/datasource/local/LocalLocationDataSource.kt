package io.apptolast.paparcar.data.datasource.local

import io.apptolast.paparcar.data.datasource.local.room.LocationEntity

interface LocalLocationDataSource {

    suspend fun insert(location: LocationEntity)

    suspend fun getAll(): List<LocationEntity>

    suspend fun deleteAll()
}
