package com.pureplus.delivery.domain

import com.pureplus.delivery.data.remote.OrderExceptionDto
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DeliveryRepository

/**
 * DeliveryFailGate — the DP-12 anti-abuse gate in front of a FAILED_DELIVERY exception.
 *
 * Backend rule (source of truth: `OrderExceptionService.RaiseAsync` for `FAILED_DELIVERY`):
 *   1. the order must be OUT_FOR_DELIVERY (the driver has departed the store);
 *   2. at least ONE contact attempt must be recorded (`POST /orders/{id}/attempts`); and
 *   3. UNLESS the reason contains "refus" (customer refused at the door), at least 5 minutes must have
 *      passed since the order went OUT_FOR_DELIVERY.
 * The server enforces all three and returns a plain-English 400 if any fails. We DO NOT bypass it.
 *
 * This use-case gives the UI a cheap client-side PREVIEW of rules 2 and 3 so the driver sees a readable
 * reason *before* a round-trip, and then still routes the actual raise through the server (which is the
 * authority — rule 1 and the exact minutes are only known there).
 *
 * Feature-detection: none. The fail-gate is identical for legacy and ShiftV2 stores (it is about orders,
 * not the duty shift), so there is nothing to branch on.
 */
class DeliveryFailGate(
    private val delivery: DeliveryRepository,
) {

    companion object {
        /** Minimum dwell at the customer before a non-refusal failure is allowed. Mirrors the backend 5 min. */
        const val MIN_MINUTES_AT_CUSTOMER = 5L
    }

    /** What the client knows locally about an in-progress "unable to deliver" attempt. */
    data class FailContext(
        /** How many contact attempts have been recorded for this order in this session. */
        val recordedAttempts: Int,
        /** Minutes since the order went OUT_FOR_DELIVERY, if the UI is tracking it; null if unknown. */
        val minutesSinceOutForDelivery: Long?,
        /** The reason the driver picked (free text or a canned option). */
        val reason: String?,
    )

    /** The result of the client-side pre-check: allowed to try, or blocked with a human reason. */
    sealed interface Gate {
        data object Allowed : Gate
        data class Blocked(val reason: String) : Gate
    }

    /** True when the reason signals the customer refused at the door (waives the 5-minute wait). */
    fun isRefusal(reason: String?): Boolean =
        (reason ?: "").lowercase().contains("refus")

    /**
     * Client-side preview of the gate. Returns [Gate.Allowed] when the known facts satisfy the rules, or
     * [Gate.Blocked] with the same human wording the driver would otherwise hit as a server 400. Rule 1
     * (OUT_FOR_DELIVERY) is intentionally NOT checked here — only the server knows the authoritative
     * lifecycle status — so a pass here still defers to the server.
     */
    fun canFail(ctx: FailContext): Gate {
        if (ctx.recordedAttempts < 1) {
            return Gate.Blocked(
                "Call the customer and record at least one contact attempt before marking the delivery failed."
            )
        }
        if (!isRefusal(ctx.reason)) {
            val mins = ctx.minutesSinceOutForDelivery
            if (mins != null && mins < MIN_MINUTES_AT_CUSTOMER) {
                return Gate.Blocked(
                    "Please allow at least 5 minutes at the customer ($mins min so far) before marking it failed. " +
                        "If the customer refused, select \"Customer Refused\" instead."
                )
            }
        }
        return Gate.Allowed
    }

    /**
     * Record a contact attempt (rule 2). Online-first by design — the server reads the attempt count for
     * the gate, so this is never queued offline. Returns the new attempt id.
     */
    suspend fun recordAttempt(orderId: Long, outcome: String?, notes: String? = null): ApiResult<Int> =
        delivery.recordAttempt(orderId, outcome, notes)

    /**
     * Raise the FAILED_DELIVERY exception. We run the client-side [canFail] preview first (so an obviously
     * premature fail is caught without a round-trip), then call the server, which re-enforces ALL three
     * rules. Any server 400 flows back with its verbatim message. On success the order has already moved
     * to FAILED server-side.
     */
    suspend fun raiseFailed(
        orderId: Long,
        reason: String?,
        notes: String?,
        ctx: FailContext,
    ): ApiResult<OrderExceptionDto> {
        when (val gate = canFail(ctx)) {
            is Gate.Blocked -> return ApiResult.Failure(gate.reason)
            Gate.Allowed -> Unit
        }
        return delivery.raiseException(orderId, OrderExceptionTypes.FAILED_DELIVERY, reason, notes)
    }
}

/** Valid WA-05 exception categories — mirrors the backend `OrderExceptionTypes`. */
object OrderExceptionTypes {
    const val FAILED_DELIVERY = "FAILED_DELIVERY"
    const val CANCEL_REQUEST = "CANCEL_REQUEST"
    const val ADDRESS_CHANGE = "ADDRESS_CHANGE"
    const val FLOOR_DISPUTE = "FLOOR_DISPUTE"
    const val STOCK_SHORTFALL = "STOCK_SHORTFALL"
}
