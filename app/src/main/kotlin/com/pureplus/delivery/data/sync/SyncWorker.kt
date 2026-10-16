package com.pureplus.delivery.data.sync

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.pureplus.delivery.data.local.AppDatabase
import com.pureplus.delivery.data.local.OutboxEntry
import com.pureplus.delivery.data.local.OutboxOperation
import com.pureplus.delivery.data.local.OutboxStatus
import com.pureplus.delivery.data.remote.*
import retrofit2.Response
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that flushes the outbox in creation order on connectivity.
 *
 * Idempotency: DELIVER + CONFIRM_PAYMENT carry an idempotencyKey in their body —
 * the backend ignores duplicates, so retries are safe.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val dao  = AppDatabase.getInstance(appContext).outboxDao()
    private val api  = RetrofitClient.apiService
    private val gson = Gson()

    override suspend fun doWork(): Result {
        val pending = dao.getPending()
        if (pending.isEmpty()) return Result.success()

        for (entry in pending) {
            try {
                dispatch(entry)
                dao.updateStatus(entry.id, OutboxStatus.SYNCED)
            } catch (e: Exception) {
                val retries = entry.retryCount + 1
                val newStatus = if (retries >= 3) OutboxStatus.FAILED else OutboxStatus.PENDING
                dao.updateStatus(entry.id, newStatus, e.message)
                if (newStatus == OutboxStatus.FAILED) continue else return Result.retry()
            }
        }

        dao.clearSynced()
        return Result.success()
    }

    private suspend fun dispatch(entry: OutboxEntry) {
        when (entry.operation) {
            OutboxOperation.DELIVER -> {
                // Server-idempotent: a replay returns alreadyRecorded=true, so retries are safe.
                val req = gson.fromJson(entry.bodyJson, RecordDeliveryRequest::class.java)
                api.deliver(entry.url, req).ensureOk("deliver")
            }
            OutboxOperation.CONFIRM_PAYMENT -> {
                api.confirmPayment(entry.url).ensureOk("confirm-payment")
            }
            OutboxOperation.EXCEPTION -> {
                val req = gson.fromJson(entry.bodyJson, RaiseExceptionRequest::class.java)
                api.raiseException(entry.url, req).ensureOk("exception")
            }
            OutboxOperation.CAN_LINES -> {
                val req = gson.fromJson(entry.bodyJson, SetCanLineRequest::class.java)
                api.setCanLine(entry.url, req).ensureOk("can-lines")
            }
            OutboxOperation.CASH_TALLY -> {
                val req = gson.fromJson(entry.bodyJson, SetCashRequest::class.java)
                api.setCash(entry.url, req).ensureOk("cash")
            }
            OutboxOperation.CLOSE_SESSION -> {
                val req = gson.fromJson(entry.bodyJson, CloseSessionRequest::class.java)
                api.closeSession(entry.url, req).ensureOk("close")
            }
            OutboxOperation.DECLARE_SESSION -> {
                // ShiftV2 terminal write — no body. The state transition (OPEN→DRIVER_DECLARED) is
                // idempotent server-side, so a replay after reconnect is safe.
                api.declareSession(entry.url).ensureOk("declare")
            }
            else -> error("Unknown outbox operation: ${entry.operation}")
        }
    }

    private fun <T> Response<ApiResponse<T>>.ensureOk(label: String) {
        if (!isSuccessful) error("$label failed: HTTP ${code()}")
        val body = body()
        check(body?.success != false) { body?.message ?: "$label failed" }
    }

    companion object {
        const val WORK_NAME = "pureplus_sync"

        fun scheduleImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun schedulePeriodicOnConnectivity(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "${WORK_NAME}_periodic",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
