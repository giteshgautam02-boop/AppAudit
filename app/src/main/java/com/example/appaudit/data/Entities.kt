package com.example.appaudit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row = a package we've seen installed before.
 * This is the "last known snapshot" we diff against on every scan.
 */
@Entity(tableName = "known_packages")
data class KnownPackage(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val firstSeen: Long
)

/**
 * One row = an app that disappeared between two scans, enriched with
 * whatever UsageStatsManager history we could find for it.
 */
@Entity(tableName = "uninstalled_apps")
data class UninstalledApp(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val firstSeen: Long,
    val lastActive: Long,
    val detectedAt: Long,
    val activeDurationMs: Long,
    val eventCount: Int
)
