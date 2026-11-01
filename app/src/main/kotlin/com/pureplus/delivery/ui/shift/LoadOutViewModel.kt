package com.pureplus.delivery.ui.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.LoadOutLine
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.data.repository.ReconciliationRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One product row on the load-out sheet: how many are required vs how many the driver is loading. */
data class LoadOutRow(
    val productId: Int,
    val productName: String,
    val required: Int,
    val loaded: Int,
) {
    val short: Int get() = (required - loaded).coerceAtLeast(0)
}

data class LoadOutState(
    val loading: Boolean = true,
    val rows: List<LoadOutRow> = emptyList(),
    val submitting: Boolean = false,
    val done: Boolean = false,
    val sessionId: Long? = null,
    val error: String? = null,
) {
    val shortages: List<LoadOutRow> get() = rows.filter { it.short > 0 }
}

/**
 * DP-04 · Loading confirmation. The required quantity per product is derived from vehicle-stock's
 * `waitingOrders` — the backend's authoritative list of this driver's ASSIGNED orders not yet covered
 * by on-board stock (GET /api/delivery/vehicle-stock). The DP-05 stop LIST carries no line items
 * (only the DP-06 stop detail does), so aggregating stops here always yielded nothing. The driver
 * confirms or corrects what physically left the store, which becomes the reconciliation baseline.
 */
class LoadOutViewModel(
    private val driver: DriverRepository,
    private val recon: ReconciliationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoadOutState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = driver.vehicleStock()) {
                is ApiResult.Success -> {
                    // Product names live on the stock `lines`; waitingOrders carry only ids + quantities.
                    val names = r.data.lines.associate { it.productId to it.productName }
                    val required = LinkedHashMap<Int, LoadOutRow>()
                    r.data.waitingOrders.forEach { w ->
                        val existing = required[w.productId]
                        val qty = (existing?.required ?: 0) + w.quantity
                        required[w.productId] = LoadOutRow(
                            productId = w.productId,
                            productName = names[w.productId] ?: "Product ${w.productId}",
                            required = qty,
                            loaded = qty,
                        )
                    }
                    _state.update { it.copy(loading = false, rows = required.values.toList()) }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun setLoaded(productId: Int, loaded: Int) = _state.update { s ->
        s.copy(rows = s.rows.map { if (it.productId == productId) it.copy(loaded = loaded.coerceAtLeast(0)) else it })
    }

    fun confirm() {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val lines = _state.value.rows.map { LoadOutLine(it.productId, it.loaded) }
            when (val r = recon.loadOut(lines)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, done = true, sessionId = r.data.id.toLong()) }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LoadOutViewModel(Deps.driverRepository, Deps.reconciliationRepository) }
        }
    }
}
