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

data class StopDetailState(
    val loading: Boolean = true,
    val stop: Stop? = null,
    val error: String? = null,
)

/** DP-06 · Stop / order detail — everything needed to execute one delivery. */
class StopDetailViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(StopDetailState())
    val state = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = driver.stopDetail(orderId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, stop = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { StopDetailViewModel(Deps.driverRepository) } }
    }
}
