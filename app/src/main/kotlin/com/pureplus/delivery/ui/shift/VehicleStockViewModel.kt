package com.pureplus.delivery.ui.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.VehicleReturn
import com.pureplus.delivery.data.remote.VehicleReturnLine
import com.pureplus.delivery.data.remote.VehicleReturnRequest
import com.pureplus.delivery.data.remote.VehicleStock
import com.pureplus.delivery.data.remote.VehicleTrip
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable "hand cans back to the store" row — bounded by what's free / empty on the vehicle. */
data class ReturnRow(
    val productId: Int,
    val productName: String,
    val free: Int,            // fulls that can be returned (onBoard − reserved); reserved cans are locked to OFD orders
    val emptiesOnBoard: Int,
    val fulls: Int = 0,
    val empties: Int = 0,
) {
    val hasInput: Boolean get() = fulls > 0 || empties > 0
}

data class VehicleStockState(
    val loading: Boolean = true,
    val stock: VehicleStock? = null,
    val trips: List<VehicleTrip> = emptyList(),
    val returns: List<VehicleReturn> = emptyList(),
    val returnRows: List<ReturnRow> = emptyList(),
    val submitting: Boolean = false,
    val message: String? = null,    // success feedback after a return
    val error: String? = null,
)

/**
 * DP-03 / DP-15 · Vehicle stock — the driver's live van inventory plus load-out trip history, return
 * history, and the "record a return to the store" action. Wires all four vehicle-stock endpoints:
 * GET /vehicle-stock (snapshot), GET /vehicle-stock/trips, GET /vehicle-stock/returns, and
 * POST /vehicle-stock/return. Returning requires an open shift session (the backend says so verbatim
 * when there isn't one); only FREE fulls (not reserved to out-for-delivery orders) can be handed back.
 */
class VehicleStockViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(VehicleStockState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val stock = driver.vehicleStock()) {
                is ApiResult.Success -> {
                    val trips = (driver.vehicleTrips() as? ApiResult.Success)?.data ?: emptyList()
                    val returns = (driver.vehicleReturns() as? ApiResult.Success)?.data ?: emptyList()
                    _state.update {
                        it.copy(
                            loading = false,
                            stock = stock.data,
                            trips = trips,
                            returns = returns,
                            returnRows = stock.data.toReturnRows(),
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = stock.message) }
            }
        }
    }

    fun setFulls(productId: Int, v: Int) = edit(productId) { it.copy(fulls = v.coerceIn(0, it.free)) }
    fun setEmpties(productId: Int, v: Int) = edit(productId) { it.copy(empties = v.coerceIn(0, it.emptiesOnBoard)) }

    /** POST the return (undelivered fulls + collected empties). The response is the refreshed snapshot. */
    fun recordReturn() {
        if (_state.value.submitting) return
        val lines = _state.value.returnRows.filter { it.hasInput }
            .map { VehicleReturnLine(it.productId, it.fulls, it.empties) }
        if (lines.isEmpty()) {
            _state.update { it.copy(error = "Enter at least one can to return.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null, message = null) }
            when (val r = driver.recordReturn(VehicleReturnRequest(lines = lines))) {
                is ApiResult.Success -> {
                    // Refresh trips/returns alongside the returned snapshot so history reflects the new return.
                    val trips = (driver.vehicleTrips() as? ApiResult.Success)?.data ?: _state.value.trips
                    val returns = (driver.vehicleReturns() as? ApiResult.Success)?.data ?: _state.value.returns
                    _state.update {
                        it.copy(
                            submitting = false,
                            stock = r.data,
                            trips = trips,
                            returns = returns,
                            returnRows = r.data.toReturnRows(),
                            message = "Returned to store.",
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }

    private fun edit(productId: Int, transform: (ReturnRow) -> ReturnRow) = _state.update { s ->
        s.copy(returnRows = s.returnRows.map { if (it.productId == productId) transform(it) else it })
    }

    private fun VehicleStock.toReturnRows(): List<ReturnRow> = lines
        .filter { it.free > 0 || it.emptiesOnBoard > 0 }
        .map {
            ReturnRow(
                productId = it.productId,
                productName = it.productName ?: "Product ${it.productId}",
                free = it.free,
                emptiesOnBoard = it.emptiesOnBoard,
            )
        }

    companion object {
        val Factory = viewModelFactory { initializer { VehicleStockViewModel(Deps.driverRepository) } }
    }
}
