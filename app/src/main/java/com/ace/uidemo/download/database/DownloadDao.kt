package com.ace.uidemo.download.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE tabId = :tabId")
    fun getDownloadsByTab(tabId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("DELETE FROM downloads WHERE tabId = :tabId")
    suspend fun deleteByTab(tabId: String)
}
