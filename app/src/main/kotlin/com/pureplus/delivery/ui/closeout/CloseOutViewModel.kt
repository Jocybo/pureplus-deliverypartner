package com.pureplus.delivery.ui.closeout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.CashSummary
import com.pureplus.delivery.data.remote.ReconciliationSession
import com.pureplus.delivery.data.remote.SetCanLineRequest
import com.pureplus.delivery.data.remote.SetCashRequest
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.data.repository.ReconciliationRepository
import com.pureplus.delivery.di.Deps
import com.pureplus.delivery.domain.ReconciliationFlow
import com.pureplus.delivery.domain.ShiftLifecycle
import com.pureplus.delivery.domain.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── DP-15 · Can return ───────────────────────────────────────────────────────

/** One editable can-reconciliation row (prefilled from the load-out baseline). */
data class CanRow(
    val productId: Int,
    val loadedOut: Int,
    val delivered: Int,
    val fullsReturned: Int,
    val emptiesCollected: Int,
    val breakageLoss: Int,
) {
    /** loaded_out − (delivered + fulls_returned + breakage_loss) — 0 means the line balances. */
    val variance: Int get() = loadedOut - (delivered + fullsReturned + breakageLoss)
}

data class CanReturnState(
    val loading: Boolean = true,
    val sessionId: Long = -1,
    val rows: List<CanRow> = emptyList(),
    val submitting: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
) {
    val unaccounted: Int get() = rows.sumOf { it.variance.coerceAtLeast(0) }
}

class CanReturnViewModel(private val recon: ReconciliationRepository) : ViewModel() {
    private val _state = MutableStateFlow(CanReturnState())
    val state = _state.asStateFlow()

    init { open() }

    fun open() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = recon.openSession()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, sessionId = r.data.id.toLong(), rows = r.data.toRows()) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun setEmpties(productId: Int, v: Int) = edit(productId) { it.copy(emptiesCollected = v.coerceAtLeast(0)) }
    fun setFullsReturned(productId: Int, v: Int) = edit(productId) { it.copy(fullsReturned = v.coerceAtLeast(0)) }
    fun setBreakage(productId: Int, v: Int) = edit(productId) { it.copy(breakageLoss = v.coerceAtLeast(0)) }

    fun submit() {
        if (_state.value.submitting) return
        val sid = _state.value.sessionId
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            var error: String? = null
            for (row in _state.value.rows) {
                val res = recon.setCanLine(sid, SetCanLineRequest(row.productId, row.loadedOut, row.delivered, row.fullsReturned, row.emptiesCollected, row.breakageLoss))
                if (res is ApiResult.Failure && !res.offline) { error = res.message; break }
            }
            _state.update { it.copy(submitting = false, done = error == null, error = error) }
        }
    }

    private fun edit(productId: Int, transform: (CanRow) -> CanRow) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.productId == productId) transform(it) else it })
    }

    private fun ReconciliationSession.toRows() = lines.map {
        CanRow(it.productId, it.loadedOut, it.delivered, it.fullsReturned, it.emptiesCollected, it.breakageLoss)
    }

    companion object {
        val Factory = viewModelFactory { initializer { CanReturnViewModel(Deps.reconciliationRepository) } }
    }
}

// ── DP-16 · Cash reconciliation ──────────────────────────────────────────────

data class CashState(
    val loading: Boolean = true,
    val cash: CashSummary? = null,
    val declaredCash: String = "",
    val submitting: Boolean = false,
    val done: Boolean = false,
    /** ShiftV2: once the handover is declared the session is read-only — await store verification. */
    val declared: Boolean = false,
    /** True on a ShiftV2 store (declare handshake); false on a legacy store (direct close). */
    val shiftV2: Boolean = false,
    val error: String? = null,
)

/**
 * DP-16 · Cash reconciliation + the end-of-shift handover.
 *
 * Expected cash/UPI/postpaid/pending are read from the session (server-computed from the day's
 * deliveries) — the driver never types them. The driver only declares the physical cash handed over;
 * variance is computed server-side.
 *
 * The TERMINAL write differs by store model (feature-detected by [ReconciliationFlow]):
 *   - ShiftV2 → **declare** (OPEN → DRIVER_DECLARED): the session becomes read-only for the driver and
 *     awaits store verification; the driver still needs to clock out afterwards (DP-22).
 *   - Legacy  → **close** (OPEN → CLOSED): the old one-shot finish, then attendance check-out on DP-22.
 * We save the cash line first, then route the handover through [ReconciliationFlow.submitHandover].
 */
class CashReconcileViewModel(
    private val sessionId: Long,
    private val recon: ReconciliationRepository,
    private val reconFlow: ReconciliationFlow,
    private val shiftLifecycle: ShiftLifecycle,
) : ViewModel() {
    private val _state = MutableStateFlow(CashState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // Detect the store model up front so the primary button reads correctly (declare vs submit).
            val v2 = isShiftV2()
            when (val r = recon.getSession(sessionId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        shiftV2 = v2,
                        cash = r.data.cash,
                        declared = reconFlow.isReadOnlyForDriver(r.data),
                        declaredCash = r.data.cash?.expectedCash?.let { c -> if (c > 0) c.toLong().toString() else "" } ?: "",
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, shiftV2 = v2, error = r.message) }
            }
        }
    }

    private suspend fun isShiftV2(): Boolean =
        when (val s = shiftLifecycle.currentState()) {
            is ApiResult.Success -> s.data !is ShiftState.Legacy
            is ApiResult.Failure -> false
        }

    fun onDeclaredCash(v: String) = _state.update { it.copy(declaredCash = v.filter { c -> c.isDigit() }, error = null) }

    /**
     * Save the declared cash, then submit the end-of-shift handover (declare on ShiftV2, close on legacy).
     * On ShiftV2 success the screen flips to the read-only "awaiting store verification" state; [onNext]
     * then carries the driver to DP-22 to clock out once the store verifies.
     */
    fun submit(onNext: () -> Unit) {
        if (_state.value.submitting) return
        val declared = _state.value.declaredCash.toDoubleOrNull()
        if (declared == null) { _state.update { it.copy(error = "Enter the cash you're handing over.") }; return }
        val c = _state.value.cash
        val v2 = _state.value.shiftV2
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val req = SetCashRequest(
                expectedCash = c?.expectedCash ?: 0.0,
                expectedUpi = c?.expectedUpi ?: 0.0,
                expectedPostpaid = c?.expectedPostpaid ?: 0.0,
                expectedPending = c?.expectedPending ?: 0.0,
                declaredCash = declared,
                tolerance = c?.tolerance ?: 0.0,
            )
            // 1) Save the cash line (queues offline).
            val cashSaved = when (val r = recon.setCash(sessionId, req)) {
                is ApiResult.Success -> { _state.update { it.copy(cash = r.data.cash) }; true }
                is ApiResult.Failure ->
                    if (r.offline) true else { _state.update { it.copy(submitting = false, error = r.message) }; false }
            }
            if (!cashSaved) return@launch

            // 2) Terminal handover — declare (ShiftV2) or close (legacy).
            when (val outcome = reconFlow.submitHandover(sessionId, shiftV2 = v2)) {
                is ReconciliationFlow.HandoverOutcome.Declared ->
                    _state.update { it.copy(submitting = false, done = true, declared = true, cash = outcome.session.cash) }
                is ReconciliationFlow.HandoverOutcome.Closed ->
                    _state.update { it.copy(submitting = false, done = true, declared = false) }
                ReconciliationFlow.HandoverOutcome.DeclareQueued ->
                    _state.update { it.copy(submitting = false, done = true, declared = true) }
                is ReconciliationFlow.HandoverOutcome.Error ->
                    _state.update { it.copy(submitting = false, error = outcome.message) }
            }
            if (_state.value.done) onNext()
        }
    }

    companion object {
        fun factory(sessionId: Long) = viewModelFactory {
            initializer {
                CashReconcileViewModel(sessionId, Deps.reconciliationRepository, Deps.reconciliationFlow, Deps.shiftLifecycle)
            }
        }
    }
}

// ── DP-22 · Check-out ────────────────────────────────────────────────────────

/** The driver-facing phase this screen is in, derived from [ShiftState]. */
enum class CheckOutPhase {
    /** Still resolving the shift state. */
    Loading,

    /**
     * ShiftV2 ACTIVE (or a legacy still-open shift) with no reconciliation pending yet — offer
     * "End shift" (→ PENDING_RECON). Only safe directly when nothing was delivered; otherwise the
     * driver is routed to close-out to reconcile first.
     */
    EndShift,

    /**
     * ShiftV2 PENDING_RECON — the driver ended duty and (should have) declared the handover.
     * Offer **Clock out** (hard-gated server-side) and **Resume** (undo the end).
     */
    PendingRecon,

    /** CLOSED / AUTO_CLOSED / FORCE_CLOSED — the shift is finished for the day. Terminal. */
    Completed,

    /** Legacy store with a reconciliation session — the old both-loops-balance close + attendance. */
    LegacyClose,
}

data class CheckOutState(
    val loading: Boolean = true,
    val phase: CheckOutPhase = CheckOutPhase.Loading,
    /** null in direct mode (mistaken check-in with no run to reconcile). */
    val session: ReconciliationSession? = null,
    val directMode: Boolean = false,
    val completedToday: Int = 0,
    /** The resolved shift state (drives the banner/actions). */
    val shiftState: ShiftState? = null,
    val submitting: Boolean = false,
    /** A reversible "end shift" is in flight (distinct from the terminal clock-out). */
    val ending: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
) {
    /** Direct end-shift is only safe when the driver hasn't delivered anything today. */
    val canEndDirect: Boolean get() = directMode && completedToday == 0
    /** They ran a route → must reconcile, not slip out via the attendance-only path. */
    val blockedByActivity: Boolean get() = directMode && completedToday > 0
}

/**
 * DP-22 · Check-out. Driven off the **ShiftState** state machine ([ShiftLifecycle]), not a local flag:
 *
 *  - **ShiftV2** two-step close: **End shift** (ACTIVE → PENDING_RECON) → the driver reconciles + declares
 *    (DP-15/16) → **Clock out** (PENDING_RECON → CLOSED). Clock-out is **hard-gated on the server**
 *    (store must have verified the handover AND no orders still out) — we call the endpoint and surface the
 *    backend's 400 message **verbatim**, never a client-side guess. While PENDING_RECON the driver may
 *    **Resume** (undo the end). The terminal "shift completed" state only shows on
 *    CLOSED/AUTO_CLOSED/FORCE_CLOSED.
 *  - **Legacy** store ([session] present, no duty status): the old both-loops close then attendance.
 *  - **direct** ([sessionId] null/≤0): "checked in by mistake" — attendance-only end, allowed only when
 *    nothing was delivered today (otherwise routed to close-out).
 */
class CheckOutViewModel(
    private val sessionId: Long?,
    private val recon: ReconciliationRepository,
    private val driver: DriverRepository,
    private val shiftLifecycle: ShiftLifecycle,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckOutState())
    val state = _state.asStateFlow()

    private val direct = sessionId == null || sessionId <= 0

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            // Resolve the duty-shift state first — it decides the whole screen.
            val shiftState = (shiftLifecycle.currentState() as? ApiResult.Success)?.data

            if (direct) {
                val completed = (driver.routeSummary() as? ApiResult.Success)?.data?.stopsCompletedToday ?: 0
                _state.update {
                    it.copy(
                        loading = false,
                        directMode = true,
                        completedToday = completed,
                        shiftState = shiftState,
                        phase = phaseFor(shiftState, session = null),
                    )
                }
            } else {
                when (val r = recon.getSession(sessionId!!)) {
                    is ApiResult.Success -> _state.update {
                        it.copy(loading = false, session = r.data, shiftState = shiftState, phase = phaseFor(shiftState, r.data))
                    }
                    is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
                }
            }
        }
    }

    private fun phaseFor(shiftState: ShiftState?, session: ReconciliationSession?): CheckOutPhase = when (shiftState) {
        ShiftState.Closed, ShiftState.AutoClosed, ShiftState.ForceClosed -> CheckOutPhase.Completed
        ShiftState.PendingRecon -> CheckOutPhase.PendingRecon
        ShiftState.Active, ShiftState.OffShift -> CheckOutPhase.EndShift
        // Legacy store (or unknown): keep the old close path when there's a session, else end-shift.
        else -> if (session != null) CheckOutPhase.LegacyClose else CheckOutPhase.EndShift
    }

    /** ShiftV2: end the duty shift (ACTIVE → PENDING_RECON). Reversible via [resume]. */
    fun endShift() {
        if (_state.value.submitting || _state.value.ending) return
        viewModelScope.launch {
            _state.update { it.copy(ending = true, error = null) }
            when (val r = shiftLifecycle.endShift()) {
                is ApiResult.Success ->
                    _state.update { it.copy(ending = false, phase = CheckOutPhase.PendingRecon) }
                is ApiResult.Failure ->
                    _state.update { it.copy(ending = false, error = r.message) }
            }
        }
    }

    /** ShiftV2: undo an end done by mistake (PENDING_RECON → ACTIVE). */
    fun resume() {
        if (_state.value.submitting || _state.value.ending) return
        viewModelScope.launch {
            _state.update { it.copy(ending = true, error = null) }
            when (val r = shiftLifecycle.resume()) {
                is ApiResult.Success ->
                    _state.update { it.copy(ending = false, phase = CheckOutPhase.EndShift) }
                is ApiResult.Failure ->
                    _state.update { it.copy(ending = false, error = r.message) }
            }
        }
    }

    /**
     * Clock out. On ShiftV2 this is hard-gated by the backend (store must have verified the handover AND
     * no orders still out for delivery). We DO NOT pre-check: we call the endpoint and surface any backend
     * 400 message verbatim. On a legacy store with a session, we close the reconciliation first.
     */
    fun checkOut() {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            if (direct) {
                // Attendance-only / clock-out — the mistaken-check-in escape hatch (and ShiftV2 clock-out).
                when (val r = shiftLifecycle.clockOut()) {
                    is ApiResult.Success -> _state.update { it.copy(submitting = false, done = true) }
                    is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
                }
            } else if (_state.value.phase == CheckOutPhase.LegacyClose) {
                // Legacy: close the reconciliation (both-loops gate) then stamp attendance.
                when (val r = recon.close(sessionId!!, "Closed from driver app")) {
                    is ApiResult.Success -> {
                        shiftLifecycle.clockOut() // attendance close; best-effort
                        _state.update { it.copy(submitting = false, done = true) }
                    }
                    is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
                }
            } else {
                // ShiftV2 PENDING_RECON → clock out (gate owned by the server; message surfaced verbatim).
                when (val r = shiftLifecycle.clockOut()) {
                    is ApiResult.Success -> _state.update { it.copy(submitting = false, done = true) }
                    is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
                }
            }
        }
    }

    companion object {
        fun factory(sessionId: Long?) = viewModelFactory {
            initializer {
                CheckOutViewModel(sessionId, Deps.reconciliationRepository, Deps.driverRepository, Deps.shiftLifecycle)
            }
        }
    }
}
