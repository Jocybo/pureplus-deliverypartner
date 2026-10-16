package com.pureplus.delivery.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.DeliveryExecutionResult
import com.pureplus.delivery.data.remote.DeliveryLine
import com.pureplus.delivery.data.remote.RecordDeliveryRequest
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DeliverOutcome
import com.pureplus.delivery.data.repository.DeliveryRepository
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.di.Deps
import com.pureplus.delivery.domain.DeliverPreview
import com.pureplus.delivery.ui.stops.isPostpaid
import com.pureplus.delivery.util.isReusableProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable line at the door. Empties input only shows for reusable products. */
data class DoorLine(
    val productId: Int,
    val name: String,
    val orderedQty: Int,
    val unitPrice: Double,
    val reusable: Boolean,
    val deliveredQty: Int,
    val emptiesCollected: Int,
) {
    val lineTotal: Double get() = deliveredQty * unitPrice
    val shortfall: Int get() = if (reusable) (deliveredQty - emptiesCollected).coerceAtLeast(0) else 0
}

enum class DeliveryStep { Execution, Payment, Preview, Done }

data class DeliveryState(
    val loading: Boolean = true,
    val stop: Stop? = null,
    val lines: List<DoorLine> = emptyList(),
    val actualFloor: Int = 0,
    val actualHasLift: Boolean = false,
    val paymentMode: String = "CASH",     // CASH | UPI (POSTPAID auto for subscription/B2B)
    val step: DeliveryStep = DeliveryStep.Execution,
    val submitting: Boolean = false,
    /** The authoritative pre-commit breakdown from /deliver/preview (shown on the Preview step). */
    val preview: DeliveryExecutionResult? = null,
    val previewing: Boolean = false,
    val result: DeliveryExecutionResult? = null,
    val queuedOffline: Boolean = false,
    val error: String? = null,
) {
    val isPostpaid: Boolean get() = stop?.isPostpaid == true
    /** Client-side estimate shown before settlement; the server total is authoritative on confirm. */
    val productsSubtotal: Double get() = lines.sumOf { it.lineTotal }
    val totalShortfall: Int get() = lines.sumOf { it.shortfall }
}

/**
 * DP-08 doorstep → DP-09 payment → DP-09 **preview** (authoritative breakdown) → DP-11 complete (DP-10
 * offline is the "queued" variant of Done). The driver now sees the EXACT money the backend will charge
 * (floor delta + extra can deposit + amount payable) BEFORE committing: we call `/deliver/preview` via
 * [DeliverPreview.preview] to fill the receipt, and commit with [DeliverPreview.confirm]. The commit still
 * fires once and settles server-side.
 */
class DeliveryViewModel(
    private val orderId: Long,
    private val driver: DriverRepository,
    private val delivery: DeliveryRepository,
    private val deliverPreview: DeliverPreview,
) : ViewModel() {
    private val _state = MutableStateFlow(DeliveryState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = driver.stopDetail(orderId)) {
                is ApiResult.Success -> {
                    val stop = r.data
                    val lines = (stop.items ?: emptyList()).map { item ->
                        val reusable = isReusableProduct(item.productName)
                        DoorLine(
                            productId = item.productId,
                            name = item.productName ?: "Product ${item.productId}",
                            orderedQty = item.quantity,
                            unitPrice = item.unitPrice,
                            reusable = reusable,
                            deliveredQty = item.quantity,
                            emptiesCollected = if (reusable) item.quantity else 0,
                        )
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            stop = stop,
                            lines = lines,
                            actualFloor = stop.floorNo ?: 0,
                            actualHasLift = stop.lift ?: false,
                            paymentMode = if (stop.isPostpaid) "POSTPAID" else "CASH",
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun setDelivered(productId: Int, qty: Int) = updateLine(productId) { it.copy(deliveredQty = qty.coerceAtLeast(0)) }
    fun setEmpties(productId: Int, qty: Int) = updateLine(productId) { it.copy(emptiesCollected = qty.coerceAtLeast(0)) }
    fun setFloor(floor: Int) = _state.update { it.copy(actualFloor = floor.coerceAtLeast(0)) }
    fun setLift(hasLift: Boolean) = _state.update { it.copy(actualHasLift = hasLift) }
    fun setPaymentMode(mode: String) = _state.update { it.copy(paymentMode = mode) }

    fun toPayment() = _state.update { it.copy(step = DeliveryStep.Payment, error = null) }
    fun backToExecution() = _state.update { it.copy(step = DeliveryStep.Execution, preview = null) }
    fun backToPayment() = _state.update { it.copy(step = DeliveryStep.Payment, preview = null, error = null) }

    /** Build the deliver payload from the current door entries. */
    private fun buildRequest(): RecordDeliveryRequest {
        val s = _state.value
        return RecordDeliveryRequest(
            actualFloor = s.actualFloor,
            actualHasLift = s.actualHasLift,
            lines = s.lines.map { DeliveryLine(it.productId, it.deliveredQty, it.emptiesCollected, it.unitPrice) },
            paymentMode = s.paymentMode,
            paymentPending = false,
        )
    }

    /**
     * Fetch the authoritative breakdown WITHOUT committing, then advance to the Preview step so the driver
     * can see the exact amount payable (and tell the customer) before confirming. If the preview call fails
     * online we surface the error; the driver can still retry.
     */
    fun toPreview() {
        if (_state.value.previewing) return
        viewModelScope.launch {
            _state.update { it.copy(previewing = true, error = null) }
            when (val r = deliverPreview.preview(orderId, buildRequest())) {
                is ApiResult.Success ->
                    _state.update { it.copy(previewing = false, preview = r.data, step = DeliveryStep.Preview) }
                is ApiResult.Failure ->
                    // Offline → there's nothing to preview; let the driver confirm directly (will queue).
                    if (r.offline) _state.update { it.copy(previewing = false, preview = null, step = DeliveryStep.Preview) }
                    else _state.update { it.copy(previewing = false, error = r.message) }
            }
        }
    }

    fun confirm() {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            when (val out = deliverPreview.confirm(orderId, buildRequest())) {
                is DeliverOutcome.Recorded ->
                    _state.update { it.copy(submitting = false, step = DeliveryStep.Done, result = out.result, queuedOffline = out.result.paymentPending) }
                DeliverOutcome.Queued ->
                    _state.update { it.copy(submitting = false, step = DeliveryStep.Done, queuedOffline = true) }
                is DeliverOutcome.Error ->
                    _state.update { it.copy(submitting = false, error = out.message) }
            }
        }
    }

    /** "Customer disputes a charge?" — flags the delivery for Store-Admin review, handover still completes. */
    fun flagDispute() {
        viewModelScope.launch {
            delivery.raiseException(orderId, "FLOOR_DISPUTE", "Customer disputes a charge at the door", null)
            _state.update { it.copy(error = "Flagged for store admin review. You can still complete the handover.") }
        }
    }

    private fun updateLine(productId: Int, transform: (DoorLine) -> DoorLine) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.productId == productId) transform(it) else it })
    }

    companion object {
        fun factory(orderId: Long) = viewModelFactory {
            initializer { DeliveryViewModel(orderId, Deps.driverRepository, Deps.deliveryRepository, Deps.deliverPreview) }
        }
    }
}
