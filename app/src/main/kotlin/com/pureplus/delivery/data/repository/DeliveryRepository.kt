package com.pureplus.delivery.data.repository

import android.content.Context
import com.pureplus.delivery.data.local.OutboxOperation
import com.pureplus.delivery.data.remote.ApiConstants
import com.pureplus.delivery.data.remote.BulkDeliveryResult
import com.pureplus.delivery.data.remote.BulkJob
import com.pureplus.delivery.data.remote.DeliveryExecutionResult
import com.pureplus.delivery.data.remote.OrderExceptionDto
import com.pureplus.delivery.data.remote.RaiseExceptionRequest
import com.pureplus.delivery.data.remote.RecordAttemptRequest
import com.pureplus.delivery.data.remote.RecordDeliveryRequest
import com.pureplus.delivery.data.remote.RetrofitClient
import com.pureplus.delivery.data.remote.TransitionRequest

/**
 * The doorstep loop (DP-08/10/11/12/14). A finished handover is NEVER blocked by connectivity:
 * if the network is down the write is queued to the outbox and the driver moves on (DP-10).
 * The `/deliver` endpoint is server-idempotent (a replay returns alreadyRecorded=true), so the
 * outbox retry is safe.
 */
class DeliveryRepository(context: Context) {
    private val api = RetrofitClient.apiService
    private val outbox = OutboxRepository(context)

    /** DP-08 → DP-09/DP-11 when online; DP-10 (queued) when offline. */
    suspend fun deliver(orderId: Long, request: RecordDeliveryRequest): DeliverOutcome {
        val url = ApiConstants.orderDeliver(orderId)
        return when (val r = apiCall { api.deliver(url, request) }) {
            is ApiResult.Success -> DeliverOutcome.Recorded(r.data)
            is ApiResult.Failure ->
                if (r.offline) {
                    outbox.enqueue(OutboxOperation.DELIVER, url, request)
                    DeliverOutcome.Queued
                } else {
                    DeliverOutcome.Error(r.message)
                }
        }
    }

    /**
     * DP-08 preview: the charge breakdown (floor + deposit + payable) for the given floor/empties, WITHOUT
     * committing. Always online — a preview is never queued offline (there is nothing to persist).
     */
    suspend fun deliverPreview(orderId: Long, request: RecordDeliveryRequest): ApiResult<DeliveryExecutionResult> =
        apiCall { api.deliverPreview(ApiConstants.orderDeliverPreview(orderId), request) }

    /** DP-10 sync: confirm a payment recorded offline (no body). */
    suspend fun confirmPayment(orderId: Long): ApiResult<DeliveryExecutionResult> =
        apiCall { api.confirmPayment(ApiConstants.orderConfirmPayment(orderId)) }

    /**
     * DP-12 contact attempt: log a call before a delivery may be marked failed (≥1 required unless refused).
     * Online-first by design — the fail-gate reads this server-side, so it is NOT outbox-queued. Returns
     * the new attempt's id.
     */
    suspend fun recordAttempt(orderId: Long, outcome: String?, notes: String? = null): ApiResult<Int> =
        apiCall { api.recordAttempt(ApiConstants.orderAttempts(orderId), RecordAttemptRequest(outcome, notes)) }

    /** DP-12 unable to deliver → FAILED (WA-05 queue). Queues offline. */
    suspend fun raiseException(orderId: Long, type: String, reason: String?, notes: String?): ApiResult<OrderExceptionDto> {
        val url = ApiConstants.orderException(orderId)
        val req = RaiseExceptionRequest(type, reason, notes)
        return when (val r = apiCall { api.raiseException(url, req) }) {
            is ApiResult.Success -> r
            is ApiResult.Failure -> {
                if (r.offline) outbox.enqueue(OutboxOperation.EXCEPTION, url, req)
                r
            }
        }
    }

    /** DP-11 continue — walk the order lifecycle (e.g. OUT_FOR_DELIVERY). */
    suspend fun transition(orderId: Long, toStatus: String, remarks: String? = null): ApiResult<Map<String, Any>> =
        apiCall { api.transition(ApiConstants.orderTransition(orderId), TransitionRequest(toStatus, remarks)) }

    /** DP-14 list this driver's bulk jobs (default status OUT_FOR_DELIVERY). */
    suspend fun listBulkJobs(status: String? = "OUT_FOR_DELIVERY"): ApiResult<List<BulkJob>> =
        apiCall { api.bulkJobs(status) }

    /** DP-14 bulk/event fulfilment. */
    suspend fun bulkDeliver(bulkRequestId: Long): ApiResult<BulkDeliveryResult> =
        apiCall { api.bulkDeliver(ApiConstants.bulkDeliver(bulkRequestId)) }
}

/** Result of a DP-08 deliver: settled online, queued offline, or a hard error. */
sealed interface DeliverOutcome {
    data class Recorded(val result: DeliveryExecutionResult) : DeliverOutcome
    data object Queued : DeliverOutcome
    data class Error(val message: String) : DeliverOutcome
}
