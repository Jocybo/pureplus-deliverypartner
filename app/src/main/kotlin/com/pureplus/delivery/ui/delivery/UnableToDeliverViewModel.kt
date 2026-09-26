package com.pureplus.delivery.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DeliveryRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val submitting: Boolean = false,
    val done: Boolean = false,
    val queuedOffline: Boolean = false,
    val error: String? = null,
)

/** DP-12 · Unable to deliver. Records an honest reason → order goes to FAILED (WA-05 queue). */
class UnableToDeliverViewModel(
    private val orderId: Long,
    private val delivery: DeliveryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UnableState())
    val state = _state.asStateFlow()

    fun select(reason: String) = _state.update { it.copy(selected = reason, error = null) }
    fun onNote(v: String) = _state.update { it.copy(note = v) }

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
            when (val r = delivery.raiseException(orderId, "FAILED_DELIVERY", reason, notes)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, done = true) }
                is ApiResult.Failure ->
                    if (r.offline) _state.update { it.copy(submitting = false, done = true, queuedOffline = true) }
                    else _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }

    companion object {
        fun factory(orderId: Long) = viewModelFactory {
            initializer { UnableToDeliverViewModel(orderId, Deps.deliveryRepository) }
        }
    }
}
