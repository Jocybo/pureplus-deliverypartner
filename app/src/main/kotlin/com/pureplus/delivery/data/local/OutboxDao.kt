package com.pureplus.delivery.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: OutboxEntry): Long

    /** Returns all PENDING entries in creation order — sync flushes in this order. */
    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<OutboxEntry>

    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun observePending(): Flow<List<OutboxEntry>>

    @Query("UPDATE outbox SET status = :status, lastError = :error, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM outbox WHERE status = 'SYNCED'")
    suspend fun clearSynced()
}
