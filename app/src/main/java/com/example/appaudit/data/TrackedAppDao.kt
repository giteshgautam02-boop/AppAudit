package com.example.appaudit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackedAppDao {

    @Query("SELECT * FROM known_packages")
    suspend fun getAllKnownPackages(): List<KnownPackage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnownPackages(packages: List<KnownPackage>)

    @Query("DELETE FROM known_packages WHERE packageName = :packageName")
    suspend fun removeKnownPackage(packageName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUninstalledApp(app: UninstalledApp)

    @Query("SELECT * FROM uninstalled_apps WHERE detectedAt >= :since ORDER BY detectedAt DESC")
    suspend fun getRecentUninstalls(since: Long): List<UninstalledApp>
}
