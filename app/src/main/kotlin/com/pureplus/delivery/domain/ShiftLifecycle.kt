package com.pureplus.delivery.domain

import com.pureplus.delivery.data.remote.CheckInDto
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository

/**
 * ShiftLifecycle — the driver duty-shift state machine, with **feature-detection** so one build serves
 * both pre-ShiftV2 (legacy) and ShiftV2 stores.
 *
 * Backend source of truth:
 *   - `DeliveryShiftController` (the `/api/delivery/shifts/` surface) + `CheckInService` + `DutyShiftRules`.
 *   - The check-in row's `dutyStatus` field (`CheckInDto.dutyStatus`) is the feature flag:
 *       * null  ⇒ the store is NOT running ShiftV2. Use the legacy single check-in / single check-out
 *                 path (today's behaviour) — [ShiftState.Legacy].
 *       * value ⇒ ShiftV2. Drive the UI off the mapped [ShiftState] and use end/resume/clock-out.
 *
 * Transitions (ShiftV2), all enforced server-side — we never bypass a backend 400 client-side:
 *   - [checkIn]  OffShift → ACTIVE          (`POST /shifts/check-in`)
 *   - [endShift] ACTIVE → PENDING_RECON     (`POST /shifts/end`)
 *   - [resume]   PENDING_RECON → ACTIVE     (`POST /shifts/resume`, "ended by mistake")
 *   - [clockOut] PENDING_RECON → CLOSED     (`POST /shifts/check-out`) — only valid once reconciliation is
 *                STORE_VERIFIED and there are no orders still out for delivery. The backend owns that gate
 *                (`CheckInService.CheckOutAsync`): if it is not satisfied the server returns a 400 whose
 *                message we surface **verbatim** to the driver (do NOT re-check or soften it here).
 *
 * This use-case is pure Kotlin + coroutines. It depends only on [DriverRepository]; no Compose, no Retrofit.
 */
class ShiftLifecycle(
    private val driver: DriverRepository,
) {

    /**
     * Read today's shift and fold it into a [ShiftState]. The [CheckInDto.dutyStatus] value (or its
     * absence) decides legacy vs ShiftV2 — this is the single feature-detection point.
     */
    suspend fun currentState(shiftDate: String? = null): ApiResult<ShiftState> {
        val r = if (shiftDate != null) driver.checkInStatus(shiftDate) else driver.checkInStatus()
        return when (r) {
            is ApiResult.Success -> ApiResult.Success(mapState(r.data.checkedIn, r.data.checkIn))
            is ApiResult.Failure -> r
        }
    }

    /**
     * Map a check-in status into a [ShiftState].
     * - No check-in row at all ⇒ nothing started today. We can't yet know if the store runs ShiftV2
     *   (duty status only lives on a row), so treat it as [ShiftState.OffShift] — the check-in button is
     *   shown either way, and the first check-in's response reveals the mode.
     * - A row with `dutyStatus == null` ⇒ legacy store ⇒ [ShiftState.Legacy].
     * - A row with a duty status ⇒ ShiftV2 ⇒ the mapped state.
     */
    fun mapState(checkedIn: Boolean, checkIn: CheckInDto?): ShiftState {
        if (checkIn == null) return ShiftState.OffShift
        val duty = checkIn.dutyStatus?.let { normalize(it) }
            ?: return ShiftState.Legacy(checkedIn = checkIn.checkedOutAt.isNullOrBlank())
        return when (duty) {
            DutyStatus.ACTIVE -> ShiftState.Active
            DutyStatus.PENDING_RECON -> ShiftState.PendingRecon
            DutyStatus.CLOSED -> ShiftState.Closed
            DutyStatus.AUTO_CLOSED -> ShiftState.AutoClosed
            DutyStatus.FORCE_CLOSED -> ShiftState.ForceClosed
            // An unrecognised duty string: be conservative and treat the row as legacy-shaped so the
            // driver is never stranded with an unknown state.
            else -> ShiftState.Legacy(checkedIn = checkIn.checkedOutAt.isNullOrBlank())
        }
    }

    /** True once we have seen a real duty status — i.e. the store runs ShiftV2. */
    fun isShiftV2(checkIn: CheckInDto?): Boolean =
        checkIn?.dutyStatus?.let { normalize(it) } != null

    // ── operations ──────────────────────────────────────────────────────────────

    /**
     * Check in (geo-fenced). Works for both modes — on a ShiftV2 store the response carries a
     * `dutyStatus = ACTIVE`; on a legacy store it is null. Identity (driver/store) comes from the JWT.
     */
    suspend fun checkIn(
        lat: Double?,
        lng: Double?,
        override: Boolean = false,
        overrideReason: String? = null,
        shiftDate: String? = null,
    ): ApiResult<CheckInDto> =
        if (shiftDate != null) driver.checkIn(lat, lng, override, overrideReason, shiftDate)
        else driver.checkIn(lat, lng, override, overrideReason)

    /** ShiftV2: ACTIVE → PENDING_RECON. Reversible via [resume] until the store verifies reconciliation. */
    suspend fun endShift(shiftDate: String? = null): ApiResult<CheckInDto> =
        if (shiftDate != null) driver.endShift(shiftDate) else driver.endShift()

    /** ShiftV2: PENDING_RECON → ACTIVE — undo an end done by mistake. */
    suspend fun resume(shiftDate: String? = null): ApiResult<CheckInDto> =
        if (shiftDate != null) driver.resumeShift(shiftDate) else driver.resumeShift()

    /**
     * Clock out. Valid only once reconciliation is STORE_VERIFIED and no orders are still out for delivery
     * — but that gate lives on the server (`CheckInService.CheckOutAsync`). We DO NOT re-implement or
     * short-circuit it here: we call the endpoint and let any backend 400 flow back with its exact
     * message (e.g. "You can clock out only after the store has verified your end-of-shift handover…").
     *
     * Legacy stores use this same endpoint as their ordinary single clock-out.
     */
    suspend fun clockOut(
        lat: Double? = null,
        lng: Double? = null,
        shiftDate: String? = null,
    ): ApiResult<CheckInDto> =
        if (shiftDate != null) driver.checkOut(lat, lng, shiftDate) else driver.checkOut(lat, lng)

    private fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val k = raw.trim().uppercase().filter { it.isLetterOrDigit() }
        return when (k) {
            "ACTIVE", "CHECKEDIN", "OPEN" -> DutyStatus.ACTIVE
            "PENDINGRECON", "PENDINGRECONCILIATION" -> DutyStatus.PENDING_RECON
            "CLOSED", "CHECKEDOUT" -> DutyStatus.CLOSED
            "AUTOCLOSED" -> DutyStatus.AUTO_CLOSED
            "FORCECLOSED" -> DutyStatus.FORCE_CLOSED
            else -> null
        }
    }
}
