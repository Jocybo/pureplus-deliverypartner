package com.pureplus.delivery.ui.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StopFilter(val label: String) { All("All"), Pending("Pending"), Postpaid("Postpaid"), Failed("Failed") }

data class StopListState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val all: List<Stop> = emptyList(),
    val filter: StopFilter = StopFilter.All,
    val error: String? = null,
) {
    val doneCount: Int get() = all.count { it.status == "DELIVERED" }
    val visible: List<Stop> get() = when (filter) {
        StopFilter.All -> all
        StopFilter.Pending -> all.filter { it.status != "DELIVERED" && it.status != "FAILED" }
        StopFilter.Postpaid -> all.filter { it.paymentMode.equals("POSTPAID", true) || it.paymentTypeId == 2 }
        StopFilter.Failed -> all.filter { it.status == "FAILED" }
    }
}

/** DP-05 · Stop list / route sequence. */
class StopListViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(StopListState())
    val state = _state.asStateFlow()

    init { load() }

    /** First load — full-screen loader. */
    fun load() = fetch(initial = true)

    /** Pull-to-refresh — pull indicator, not the full-screen loader. */
    fun refresh() = fetch(initial = false)

    private fun fetch(initial: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            when (val r = driver.stops()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, refreshing = false, all = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, refreshing = false, error = r.message) }
            }
        }
    }

    fun setFilter(f: StopFilter) = _state.update { it.copy(filter = f) }

    companion object {
        val Factory = viewModelFactory { initializer { StopListViewModel(Deps.driverRepository) } }
    }
}
