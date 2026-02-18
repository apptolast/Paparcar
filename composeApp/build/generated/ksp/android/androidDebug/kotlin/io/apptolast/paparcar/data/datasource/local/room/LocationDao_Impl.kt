package io.apptolast.paparcar.`data`.datasource.local.room

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class LocationDao_Impl(
  __db: RoomDatabase,
) : LocationDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLocationEntity: EntityInsertAdapter<LocationEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfLocationEntity = object : EntityInsertAdapter<LocationEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `locations` (`id`,`latitude`,`longitude`,`accuracy`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LocationEntity) {
        statement.bindLong(1, entity.id)
        statement.bindDouble(2, entity.latitude)
        statement.bindDouble(3, entity.longitude)
        statement.bindDouble(4, entity.accuracy.toDouble())
        statement.bindLong(5, entity.timestamp)
      }
    }
  }

  public override suspend fun insert(location: LocationEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLocationEntity.insert(_connection, location)
  }

  public override suspend fun getAll(): List<LocationEntity> {
    val _sql: String = "SELECT * FROM locations ORDER BY timestamp DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfAccuracy: Int = getColumnIndexOrThrow(_stmt, "accuracy")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<LocationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocationEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpAccuracy: Float
          _tmpAccuracy = _stmt.getDouble(_columnIndexOfAccuracy).toFloat()
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item = LocationEntity(_tmpId,_tmpLatitude,_tmpLongitude,_tmpAccuracy,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM locations"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
