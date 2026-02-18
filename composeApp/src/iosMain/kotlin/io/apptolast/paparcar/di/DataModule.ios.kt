package io.apptolast.paparcar.di

import androidx.room.RoomDatabase
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase

/**
 * Implementación de getDatabaseBuilder para iOS.
 * Stub - Room en iOS requiere configuración adicional con SQLDelight o similar.
 */
actual fun getDatabaseBuilder(context: Any): RoomDatabase.Builder<AppDatabase> {
    TODO("Room para iOS no está implementado en este MVP. Solo Android es soportado por ahora.")
}
