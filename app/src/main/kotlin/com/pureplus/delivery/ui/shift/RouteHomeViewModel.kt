package com.pureplus.delivery.ui.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.GoalResponse
import com.pureplus.delivery.data.remote.RouteSummary
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouteHomeState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val summary: RouteSummary? = null,
    val goal: GoalResponse? = null,
    val nextStop: Stop? = null,
    /** Stops this driver has already delivered today (from GET /me/stops/completed). */
    val completedToday: List<Stop> = emptyList(),
    val error: String? = null,
) {
    // Prefer the authoritative completed-stops count when we have it, falling back to the summary.
    val stopsDone: Int get() = if (completedToday.isNotEmpty()) completedToday.size else (summary?.stopsCompletedToday ?: 0)
    val stopsTotal: Int get() = (summary?.stopsPending ?: 0) + (summary?.stopsCompletedToday ?: 0)
}

/** DP-03 · Today's route/home. Aggregates the shift dashboard, goal progress, and the next stop. */
class RouteHomeViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(RouteHomeState())
    val state = _state.asStateFlow()

    init { load() }

    /** First load — shows the full-screen loader. */
    fun load() = fetch(initial = true)

    /** Pull-to-refresh — shows the pull indicator, not the full-screen loader. */
    fun refresh() = fetch(initial = false)

    private fun fetch(initial: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            val summary = driver.routeSummary()
            val goal = driver.goal()
            val stops = driver.stops()
            val completed = driver.stopsCompleted()
            _state.update { s ->
                s.copy(
                    loading = false,
                    refreshing = false,
                    summary = (summary as? ApiResult.Success)?.data ?: s.summary,
                    goal = (goal as? ApiResult.Success)?.data ?: s.goal,
                    nextStop = (stops as? ApiResult.Success)?.data
                        ?.firstOrNull { it.status != "DELIVERED" && it.status != "FAILED" },
                    completedToday = (completed as? ApiResult.Success)?.data ?: s.completedToday,
                    error = (summary as? ApiResult.Failure)?.message,
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { RouteHomeViewModel(Deps.driverRepository) } }
    }
}
