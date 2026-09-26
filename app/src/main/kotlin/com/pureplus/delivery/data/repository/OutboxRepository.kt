package com.pureplus.delivery.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pureplus.delivery.data.local.AppDatabase
import com.pureplus.delivery.data.local.OutboxEntry
import com.pureplus.delivery.data.sync.SyncWorker
import kotlinx.coroutines.flow.Flow

/**
 * The offline outbox gateway. Doorstep/close-out writes that must survive network loss are enqueued
 * here (Room) and flushed in creation order by [SyncWorker] when connectivity returns.
 */
class OutboxRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).outboxDao()
    private val gson = Gson()

    /** Enqueue a mutating op and kick the sync worker. [body] is serialised to JSON. */
    suspend fun enqueue(operation: String, url: String, body: Any?, method: String = "POST") {
        dao.enqueue(
            OutboxEntry(
                operation = operation,
                url = url,
                bodyJson = body?.let { gson.toJson(it) } ?: "{}",
                method = method,
            ),
        )
        SyncWorker.scheduleImmediate(context)
    }

    fun pendingCount(): Flow<Int> = dao.observePendingCount()
}
