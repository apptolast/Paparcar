package io.apptolast.paparcar.data.datasource.local

import io.apptolast.paparcar.data.datasource.local.room.LocationDao
import io.apptolast.paparcar.data.datasource.local.room.LocationEntity

class LocalLocationDataSourceImpl(private val locationDao: LocationDao) : LocalLocationDataSource {

    override suspend fun insert(location: LocationEntity) {
        locationDao.insert(location)
    }

    override suspend fun getAll(): List<LocationEntity> {
        return locationDao.getAll()
    }

    override suspend fun deleteAll() {
        locationDao.deleteAll()
    }
}
