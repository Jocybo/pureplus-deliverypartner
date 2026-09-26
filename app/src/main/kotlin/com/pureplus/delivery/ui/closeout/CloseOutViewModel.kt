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
    val error: String? = null,
)

/**
 * Expected cash/UPI/postpaid/pending are read from the session (server-computed from the day's
 * deliveries). The driver only declares the physical cash handed over; variance is computed server-side.
 */
class CashReconcileViewModel(
    private val sessionId: Long,
    private val recon: ReconciliationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CashState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = recon.getSession(sessionId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, cash = r.data.cash, declaredCash = r.data.cash?.expectedCash?.let { c -> if (c > 0) c.toLong().toString() else "" } ?: "")
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun onDeclaredCash(v: String) = _state.update { it.copy(declaredCash = v.filter { c -> c.isDigit() }, error = null) }

    fun submit(onNext: () -> Unit) {
        if (_state.value.submitting) return
        val declared = _state.value.declaredCash.toDoubleOrNull()
        if (declared == null) { _state.update { it.copy(error = "Enter the cash you're handing over.") }; return }
        val c = _state.value.cash
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
            when (val r = recon.setCash(sessionId, req)) {
                is ApiResult.Success -> { _state.update { it.copy(submitting = false, cash = r.data.cash, done = true) }; onNext() }
                is ApiResult.Failure ->
                    if (r.offline) { _state.update { it.copy(submitting = false, done = true) }; onNext() }
                    else _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }

    companion object {
        fun factory(sessionId: Long) = viewModelFactory {
            initializer { CashReconcileViewModel(sessionId, Deps.reconciliationRepository) }
        }
    }
}

// ── DP-22 · Check-out ────────────────────────────────────────────────────────

data class CheckOutState(
    val loading: Boolean = true,
    /** null in direct mode (mistaken check-in with no run to reconcile). */
    val session: ReconciliationSession? = null,
    val directMode: Boolean = false,
    val completedToday: Int = 0,
    val submitting: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
) {
    /** Direct end-shift is only safe when the driver hasn't delivered anything today. */
    val canEndDirect: Boolean get() = directMode && completedToday == 0
    /** They ran a route → must reconcile, not slip out via the attendance-only path. */
    val blockedByActivity: Boolean get() = directMode && completedToday > 0
}

/**
 * DP-22 · Check-out. Two modes:
 *  - **reconciled** ([sessionId] > 0): close the reconciliation (cans+cash gate) then stamp attendance.
 *  - **direct** ([sessionId] null/≤0): "checked in by mistake" — stamp attendance check-out only, allowed
 *    only when no deliveries were completed today (otherwise the driver is sent to close-out). The
 *    backend's `POST /api/delivery/shifts/check-out` is independent of reconciliation, which is exactly
 *    what a no-run shift needs.
 */
class CheckOutViewModel(
    private val sessionId: Long?,
    private val recon: ReconciliationRepository,
    private val driver: DriverRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckOutState())
    val state = _state.asStateFlow()

    private val direct = sessionId == null || sessionId <= 0

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            if (direct) {
                // Guard the direct path: only end without reconciling if nothing was delivered.
                val completed = (driver.routeSummary() as? ApiResult.Success)?.data?.stopsCompletedToday ?: 0
                _state.update { it.copy(loading = false, directMode = true, completedToday = completed) }
            } else {
                when (val r = recon.getSession(sessionId!!)) {
                    is ApiResult.Success -> _state.update { it.copy(loading = false, session = r.data) }
                    is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
                }
            }
        }
    }

    fun checkOut() {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            if (direct) {
                // Attendance-only check-out (no reconciliation) — the mistaken-check-in escape hatch.
                when (val r = driver.checkOut(null, null)) {
                    is ApiResult.Success -> _state.update { it.copy(submitting = false, done = true) }
                    is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
                }
            } else {
                when (val r = recon.close(sessionId!!, "Closed from driver app")) {
                    is ApiResult.Success -> {
                        driver.checkOut(null, null) // attendance close; best-effort
                        _state.update { it.copy(submitting = false, done = true) }
                    }
                    is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
                }
            }
        }
    }

    companion object {
        fun factory(sessionId: Long?) = viewModelFactory {
            initializer { CheckOutViewModel(sessionId, Deps.reconciliationRepository, Deps.driverRepository) }
        }
    }
}
