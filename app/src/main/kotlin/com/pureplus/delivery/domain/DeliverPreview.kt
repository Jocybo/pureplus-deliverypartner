package com.pureplus.delivery.domain

import com.pureplus.delivery.data.remote.DeliveryExecutionResult
import com.pureplus.delivery.data.remote.RecordDeliveryRequest
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DeliverOutcome
import com.pureplus.delivery.data.repository.DeliveryRepository

/**
 * DeliverPreview — the authoritative charge breakdown shown on the DP-08/09 confirm step, and the actual
 * commit.
 *
 * Backend source of truth: `DeliveryOrderController.DeliverPreview` + `.Deliver`. Preview runs the SAME
 * pricing as the real deliver (floor charge delta, extra deposit for unreturned reusable cans, order
 * amount, amount payable) but commits nothing — so the driver sees the exact money BEFORE confirming, for
 * the floor/empties they actually entered at the door. The result shape is identical to the deliver call
 * (`DeliveryExecutionResult`).
 *
 * Feature-detection: none needed — preview + deliver are the same on legacy and ShiftV2 stores.
 *
 * Pure Kotlin + coroutines; depends only on [DeliveryRepository]. The preview is always online (there is
 * nothing to persist); the commit ([confirm]) goes through the repository's offline-capable path.
 */
class DeliverPreview(
    private val delivery: DeliveryRepository,
) {

    /**
     * Fetch the authoritative breakdown for the entered floor/empties WITHOUT committing. Use this to
     * populate the confirm screen's receipt.
     */
    suspend fun preview(orderId: Long, request: RecordDeliveryRequest): ApiResult<DeliveryExecutionResult> =
        delivery.deliverPreview(orderId, request)

    /**
     * Commit the delivery (the real handover). Idempotent server-side; queues to the outbox when offline
     * (handled in the repository) so a finished doorstep is never blocked by connectivity.
     */
    suspend fun confirm(orderId: Long, request: RecordDeliveryRequest): DeliverOutcome =
        delivery.deliver(orderId, request)
}
