package com.pureplus.delivery.domain

import com.pureplus.delivery.data.remote.ReconciliationSession
import com.pureplus.delivery.data.remote.SetCanLineRequest
import com.pureplus.delivery.data.remote.SetCashRequest
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.data.repository.ReconciliationRepository

/**
 * ReconciliationFlow — the driver's end-of-shift handover (cans + cash), with the ShiftV2 two-party
 * handshake and **feature-detection** so legacy stores keep working.
 *
 * Backend source of truth:
 *   - `DeliveryReconciliationController` (the `/api/delivery/reconciliation/` surface) + `ReconciliationRules`.
 *   - ShiftV2: the driver submits can-lines + cash, then **declares** (`POST sessions/{id}/declare`),
 *     moving the session OPEN → DRIVER_DECLARED. After that the session is **read-only for the driver**
 *     ("awaiting store verification"); the store verifies the physical handover (STORE_VERIFIED) and only
 *     then may the shift clock out.
 *   - Legacy (pre-ShiftV2): there is no two-party handshake — the driver **closes** the session directly
 *     (`POST sessions/{id}/close`), the old OPEN → CLOSED clean close.
 *
 * Feature-detection: the terminal write differs by store mode. We detect the mode from the duty shift's
 * `dutyStatus` (via [ShiftLifecycle]/the check-in status) — a value ⇒ ShiftV2 ⇒ [declare]; null ⇒ legacy
 * ⇒ [close]. [submitHandover] below picks the correct one, resolving the
 * `// TODO(dp-domain): close→declare under ShiftV2` marker left in the data/UI layers.
 *
 * Pure Kotlin + coroutines; depends only on repositories.
 */
class ReconciliationFlow(
    private val recon: ReconciliationRepository,
    private val driver: DriverRepository,
) {

    /** How the driver's terminal submission resolved — so the UI can show the right "what next" copy. */
    sealed interface HandoverOutcome {
        /** ShiftV2: declared. The session is now read-only for the driver; the store must verify next. */
        data class Declared(val session: ReconciliationSession) : HandoverOutcome

        /** Legacy: closed directly (clean OPEN → CLOSED). Nothing further is required from the driver. */
        data class Closed(val session: ReconciliationSession) : HandoverOutcome

        /** ShiftV2 + offline: the declare was queued to the outbox and will sync on reconnect. */
        data object DeclareQueued : HandoverOutcome

        data class Error(val message: String) : HandoverOutcome
    }

    // ── session reads / per-line writes (same for both modes) ───────────────────

    suspend fun openSession(shiftDate: String? = null): ApiResult<ReconciliationSession> =
        if (shiftDate != null) recon.openSession(shiftDate) else recon.openSession()

    suspend fun getSession(sessionId: Long): ApiResult<ReconciliationSession> = recon.getSession(sessionId)

    /** DP-15 — one can-line per product. Queues offline (handled in the repository). */
    suspend fun setCanLine(sessionId: Long, req: SetCanLineRequest): ApiResult<ReconciliationSession> =
        recon.setCanLine(sessionId, req)

    /** DP-16 — cash counts + variance. Queues offline (handled in the repository). */
    suspend fun setCash(sessionId: Long, req: SetCashRequest): ApiResult<ReconciliationSession> =
        recon.setCash(sessionId, req)

    /**
     * True once a session is read-only for the driver — i.e. it has been declared (or beyond) under
     * ShiftV2. The UI disables editing + shows "awaiting store verification" from this.
     */
    fun isReadOnlyForDriver(session: ReconciliationSession): Boolean =
        when (normalizeRecon(session.status)) {
            ReconStatus.DRIVER_DECLARED,
            ReconStatus.STORE_VERIFIED,
            ReconStatus.CLOSED,
            ReconStatus.DISPUTED -> true
            else -> false
        }

    // ── the terminal write (feature-detected) ───────────────────────────────────

    /**
     * Submit the end-of-shift handover. Detects the store mode from the duty shift and calls the right
     * terminal endpoint:
     *   - ShiftV2 (duty status present) ⇒ [ReconciliationRepository.declare] → DRIVER_DECLARED, then the
     *     session is read-only for the driver. The declare survives a network drop (outbox).
     *   - legacy  (duty status null)    ⇒ [ReconciliationRepository.close] → CLOSED (clean close).
     *
     * @param shiftV2 optional override when the caller already knows the mode (e.g. from a cached
     *   check-in). When null, we fetch the check-in status to detect it.
     */
    suspend fun submitHandover(
        sessionId: Long,
        legacyCloseNote: String? = "Closed from driver app",
        shiftV2: Boolean? = null,
    ): HandoverOutcome {
        val isV2 = shiftV2 ?: detectShiftV2()
        return if (isV2) {
            when (val r = recon.declare(sessionId)) {
                is ApiResult.Success -> HandoverOutcome.Declared(r.data)
                is ApiResult.Failure ->
                    if (r.offline) HandoverOutcome.DeclareQueued else HandoverOutcome.Error(r.message)
            }
        } else {
            when (val r = recon.close(sessionId, legacyCloseNote)) {
                is ApiResult.Success -> HandoverOutcome.Closed(r.data)
                is ApiResult.Failure -> HandoverOutcome.Error(r.message)
            }
        }
    }

    /**
     * Detect whether today's store runs ShiftV2 by reading the check-in row's `dutyStatus`.
     * Null (or a failed read we can't classify) ⇒ treat as legacy, preserving today's behaviour.
     */
    private suspend fun detectShiftV2(): Boolean =
        when (val r = driver.checkInStatus()) {
            is ApiResult.Success -> ShiftLifecycle(driver).isShiftV2(r.data.checkIn)
            is ApiResult.Failure -> false
        }

    private fun normalizeRecon(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val k = raw.trim().uppercase().filter { it.isLetterOrDigit() }
        return when (k) {
            "OPEN" -> ReconStatus.OPEN
            "DRIVERDECLARED", "DECLARED" -> ReconStatus.DRIVER_DECLARED
            "STOREVERIFIED", "VERIFIED" -> ReconStatus.STORE_VERIFIED
            "CLOSED" -> ReconStatus.CLOSED
            "DISPUTED", "DISPUTE" -> ReconStatus.DISPUTED
            else -> null
        }
    }
}

/** Canonical reconciliation statuses — mirrors the backend `ReconciliationRules`. */
object ReconStatus {
    const val OPEN = "OPEN"
    const val DRIVER_DECLARED = "DRIVER_DECLARED"
    const val STORE_VERIFIED = "STORE_VERIFIED"
    const val CLOSED = "CLOSED"
    const val DISPUTED = "DISPUTED"
}
