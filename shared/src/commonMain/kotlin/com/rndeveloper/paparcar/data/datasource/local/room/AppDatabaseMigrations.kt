package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.migration.Migration

/**
 * The migration chain. **Empty by design**, and that is a statement, not an omission:
 * [DATA-ROOM-RETURNS-TO-VERSION-ONE-001] retired the single v1→v2 step on the release wipe of
 * 2026-09-03, because `1.json` and `2.json` carried the same identity hash — v2 was a step up from
 * a v1 that no device will ever hold again.
 *
 * The list stays, and both platform builders keep registering it, so the next schema change has one
 * obvious place to land instead of rediscovering where migrations go. That plumbing was bought with
 * a real field failure — see [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001], where a column added
 * without a version bump left the safety net unable to read sessions with a parked car being
 * watched — and it survives the collapse untouched.
 *
 * ⛔ One list, both platforms. Every builder also carries
 * `fallbackToDestructiveMigration(dropAllTables = true)`, so a version bump whose migration is
 * missing from a builder does not crash there — it silently WIPES that device. A single shared list
 * is what keeps that from ever being a per-platform accident.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
