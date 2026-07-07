package com.freshnessai.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*

@Database(entities = [ScanRecord::class], version = 1, exportSchema = false)
abstract class ScanHistoryDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: ScanHistoryDatabase? = null

        fun getDatabase(context: Context): ScanHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanHistoryDatabase::class.java,
                    "freshness_scan_history"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_history ORDER BY scanTimestamp DESC")
    fun getAllScans(): LiveData<List<ScanRecord>>

    @Query("SELECT * FROM scan_history ORDER BY scanTimestamp DESC LIMIT :limit")
    fun getRecentScans(limit: Int): LiveData<List<ScanRecord>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getScanById(id: Long): ScanRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(record: ScanRecord): Long

    @Delete
    suspend fun deleteScan(record: ScanRecord)

    @Query("DELETE FROM scan_history")
    suspend fun deleteAllScans()

    @Query("SELECT COUNT(*) FROM scan_history")
    suspend fun getScanCount(): Int
}
