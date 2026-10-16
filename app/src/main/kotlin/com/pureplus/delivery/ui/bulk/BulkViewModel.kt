package com.pureplus.delivery.ui.bulk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.BulkJob
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DeliveryRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BulkListState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val jobs: List<BulkJob> = emptyList(),
    /** The job currently being fulfilled (its id), null when none is in flight. */
    val confirmingId: Int? = null,
    /** A job just fulfilled — show a one-line success and drop it from the list. */
    val lastDeliveredId: Int? = null,
    val error: String? = null,
)

/**
 * DP-14 · Bulk / event delivery. The **live entry point** is the list of this driver's bulk jobs
 * (`GET /bulk-jobs?status=OUT_FOR_DELIVERY`). Confirming a job fulfils it via the clean delivery path
 * (`POST /bulk-jobs/{id}/deliver`): consolidated deposit, B2B→postpaid, and a scheduled empty-can pickup.
 * Only jobs actually out for delivery appear, so there is no client-side status guessing.
 */
class BulkViewModel(
    private val delivery: DeliveryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BulkListState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() = fetch(initial = true)
    fun refresh() = fetch(initial = false)

    private fun fetch(initial: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            when (val r = delivery.listBulkJobs()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, refreshing = false, jobs = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, refreshing = false, error = r.message) }
            }
        }
    }

    /** Fulfil one bulk job. On success it's removed from the live list. */
    fun confirm(bulkRequestId: Int) {
        if (_state.value.confirmingId != null) return
        viewModelScope.launch {
            _state.update { it.copy(confirmingId = bulkRequestId, error = null) }
            when (val r = delivery.bulkDeliver(bulkRequestId.toLong())) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        confirmingId = null,
                        lastDeliveredId = bulkRequestId,
                        jobs = it.jobs.filter { j -> j.id != bulkRequestId },
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(confirmingId = null, error = r.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { BulkViewModel(Deps.deliveryRepository) } }
    }
}
