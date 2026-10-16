package com.pureplus.delivery.domain

/**
 * The driver's duty-shift state, as the UI should drive off it. This is the DOMAIN view of the backend
 * `DutyShiftRules` state machine (`web/PurePlus/.../Domain/DutyShiftRules.cs`) — NOT a local
 * boolean/elapsed-time heuristic.
 *
 * Feature-detection lives here: the backend `GET /shifts/check-in/status` returns a check-in row whose
 * `dutyStatus` is **null on pre-ShiftV2 stores** and a real status on ShiftV2 stores. One app build serves
 * both:
 *   - [Legacy]  — `dutyStatus == null`: the store does not run the two-step end/reconcile/clock-out
 *                 handshake. The UI uses the old single check-in / single check-out path.
 *   - everything else is a ShiftV2 state, mapped 1:1 from the canonical backend status.
 */
sealed interface ShiftState {

    /** Pre-ShiftV2 store (duty status absent). [checkedIn] drives the old single-button check-in/out UI. */
    data class Legacy(val checkedIn: Boolean) : ShiftState

    /** No shift started today on a ShiftV2 store (no check-in row, or the row carries no duty status yet). */
    data object OffShift : ShiftState

    /** ShiftV2 ACTIVE — checked in, on duty, can work stops and deliver. */
    data object Active : ShiftState

    /** ShiftV2 PENDING_RECON — the driver ended duty; now settling cans + cash. Reversible via resume. */
    data object PendingRecon : ShiftState

    /** ShiftV2 CLOSED — reconciliation store-verified AND clocked out. Terminal for the day. */
    data object Closed : ShiftState

    /** ShiftV2 AUTO_CLOSED — the end-of-day sweep closed a shift the driver forgot to end. */
    data object AutoClosed : ShiftState

    /** ShiftV2 FORCE_CLOSED — the store admin closed the shift with a reason. */
    data object ForceClosed : ShiftState
}

/**
 * Canonical ShiftV2 duty-status strings. These MUST match the backend `DutyShiftRules` constants verbatim
 * (`web/PurePlus/.../Domain/DutyShiftRules.cs`) — they are what the server sends and accepts.
 */
object DutyStatus {
    const val ACTIVE = "ACTIVE"
    const val PENDING_RECON = "PENDING_RECON"
    const val CLOSED = "CLOSED"
    const val AUTO_CLOSED = "AUTO_CLOSED"
    const val FORCE_CLOSED = "FORCE_CLOSED"
}
