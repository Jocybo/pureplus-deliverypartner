package com.pureplus.delivery.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.di.Deps
import com.pureplus.delivery.domain.DeliveryFailGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A call outcome the driver can log as a contact attempt (mirrors the backend attempt vocabulary). */
data class AttemptOption(val value: String, val label: String)

data class UnableState(
    val reasons: List<String> = listOf(
        "Customer absent",
        "Customer refused",
        "Address not found",
        "Access denied · gate, security",
        "Customer cannot pay",
        "Stock shortfall on vehicle",
    ),
    val selected: String? = null,
    val note: String = "",
    // ── call-attempt capture (the fail-gate's first requirement) ──
    val attemptOptions: List<AttemptOption> = listOf(
        AttemptOption("NO_ANSWER", "No answer"),
        AttemptOption("REFUSED", "Customer refused"),
        AttemptOption("WRONG_NUMBER", "Wrong number"),
        AttemptOption("WILL_RECEIVE", "Will receive"),
    ),
    /** How many contact attempts have been recorded this session (drives the gate). */
    val recordedAttempts: Int = 0,
    val recordingAttempt: Boolean = false,
    val submitting: Boolean = false,
    val done: Boolean = false,
    val queuedOffline: Boolean = false,
    /** A human reason the Fail button is blocked right now (null = allowed), from the fail-gate preview. */
    val blockedReason: String? = null,
    val error: String? = null,
) {
    /** The fail-gate's local preview: at least one recorded attempt is required before a fail. */
    val canFail: Boolean get() = recordedAttempts >= 1 && blockedReason == null
}

/**
 * DP-12 · Unable to deliver, gated by the backend FAILED_DELIVERY rule via [DeliveryFailGate]:
 *   1. the order must be OUT_FOR_DELIVERY (server-checked);
 *   2. at least ONE contact attempt must be recorded (`recordAttempt`); and
 *   3. unless the customer refused, at least 5 minutes must have passed at the door.
 *
 * The screen first makes the driver **log a call attempt**, then the Fail button is enabled by the gate's
 * local preview (`canFail`). The actual fail is raised through [DeliveryFailGate.raiseFailed], which
 * re-enforces all three rules server-side; any backend refusal flows back verbatim.
 */
class UnableToDeliverViewModel(
    private val orderId: Long,
    private val failGate: DeliveryFailGate,
) : ViewModel() {
    private val _state = MutableStateFlow(UnableState())
    val state = _state.asStateFlow()

    fun select(reason: String) = _state.update { it.copy(selected = reason, error = null, blockedReason = recomputeGate(it.copy(selected = reason))) }
    fun onNote(v: String) = _state.update { it.copy(note = v) }

    /** Log a contact attempt (fail-gate rule 2). Online-first; the server owns the gate. */
    fun recordAttempt(outcome: String) {
        if (_state.value.recordingAttempt) return
        viewModelScope.launch {
            _state.update { it.copy(recordingAttempt = true, error = null) }
            when (val r = failGate.recordAttempt(orderId, outcome, _state.value.note.ifBlank { null })) {
                is ApiResult.Success -> _state.update {
                    val next = it.copy(recordingAttempt = false, recordedAttempts = it.recordedAttempts + 1)
                    next.copy(blockedReason = recomputeGate(next))
                }
                is ApiResult.Failure -> _state.update { it.copy(recordingAttempt = false, error = r.message) }
            }
        }
    }

    /** Re-run the gate preview with the facts we know (attempts + chosen reason). */
    private fun recomputeGate(s: UnableState): String? {
        val ctx = DeliveryFailGate.FailContext(
            recordedAttempts = s.recordedAttempts,
            // We don't track exact minutes-at-door on this screen; the server enforces the 5-min rule.
            minutesSinceOutForDelivery = null,
            reason = s.selected,
        )
        return when (val gate = failGate.canFail(ctx)) {
            is DeliveryFailGate.Gate.Blocked -> gate.reason
            DeliveryFailGate.Gate.Allowed -> null
        }
    }

    /** [reschedule]=true records the same failure but tags it as a reschedule request for the store. */
    fun submit(reschedule: Boolean) {
        val reason = _state.value.selected
        if (reason == null) {
            _state.update { it.copy(error = "Pick a reason first.") }
            return
        }
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val notes = buildString {
                if (reschedule) append("Reschedule requested. ")
                append(_state.value.note)
            }.trim().ifBlank { null }
            val ctx = DeliveryFailGate.FailContext(
                recordedAttempts = _state.value.recordedAttempts,
                minutesSinceOutForDelivery = null,
                reason = reason,
            )
            when (val r = failGate.raiseFailed(orderId, reason, notes, ctx)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, done = true) }
                is ApiResult.Failure ->
                    // recordAttempt + raise are online-first through the gate; a genuine offline queue of the
                    // underlying exception still reports as a Failure with offline=true from the repository.
                    if (r.offline) _state.update { it.copy(submitting = false, done = true, queuedOffline = true) }
                    else _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }

    companion object {
        fun factory(orderId: Long) = viewModelFactory {
            initializer { UnableToDeliverViewModel(orderId, Deps.deliveryFailGate) }
        }
    }
}
