package com.pureplus.delivery.ui.bulk

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

data class BulkState(
    val submitting: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * DP-14 · Bulk / event delivery. Fulfils an approved bulk request (consolidated deposit + B2B→postpaid
 * + schedules the empty-can pickup). The bulk-request detail (qty/venue/balance) has no delivery-surface
 * GET yet, so this confirms fulfilment against the bulk-request id (see STATUS.md · missing APIs).
 */
class BulkViewModel(
    private val bulkRequestId: Long,
    private val delivery: DeliveryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BulkState())
    val state = _state.asStateFlow()

    fun confirm() {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            when (val r = delivery.bulkDeliver(bulkRequestId)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, done = true) }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }

    companion object {
        fun factory(bulkRequestId: Long) = viewModelFactory {
            initializer { BulkViewModel(bulkRequestId, Deps.deliveryRepository) }
        }
    }
}
