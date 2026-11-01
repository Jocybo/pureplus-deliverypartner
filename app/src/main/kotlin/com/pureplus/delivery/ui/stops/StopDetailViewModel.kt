package com.pureplus.delivery.ui.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DeliveryRepository
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StopDetailState(
    val loading: Boolean = true,
    val stop: Stop? = null,
    val error: String? = null,
    /** A "Start delivery" (ASSIGNED → OUT_FOR_DELIVERY) transition is in flight. */
    val starting: Boolean = false,
    /** Error from the start-delivery transition (e.g. the van isn't loaded) — surfaced verbatim. */
    val actionError: String? = null,
)

/** DP-06 · Stop / order detail — everything needed to execute one delivery. */
class StopDetailViewModel(
    private val driver: DriverRepository,
    private val delivery: DeliveryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StopDetailState())
    val state = _state.asStateFlow()

    private var orderId: Long = 0

    fun load(orderId: Long) {
        this.orderId = orderId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = driver.stopDetail(orderId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, stop = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    /**
     * Take the order OUT_FOR_DELIVERY before opening the doorstep flow. The lifecycle allows
     * ASSIGNED → OUT_FOR_DELIVERY → DELIVERED only (no skipping), and the backend gates OFD on the
     * van actually holding the order's cans (load-out). If the stop is already OFD we skip straight
     * to the door; any transition failure (e.g. "load out at the store first") is shown verbatim.
     */
    fun startDelivery(onReady: () -> Unit) {
        val stop = _state.value.stop ?: return
        if (_state.value.starting) return
        if (!stop.isAssigned) { onReady(); return }   // already dispatched → go to the door
        viewModelScope.launch {
            _state.update { it.copy(starting = true, actionError = null) }
            when (val r = delivery.transition(orderId, "OUT_FOR_DELIVERY")) {
                is ApiResult.Success -> {
                    _state.update { it.copy(starting = false, stop = it.stop?.copy(status = "OUT_FOR_DELIVERY")) }
                    onReady()
                }
                is ApiResult.Failure -> _state.update { it.copy(starting = false, actionError = r.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StopDetailViewModel(Deps.driverRepository, Deps.deliveryRepository) }
        }
    }
}
