package com.pureplus.delivery.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NavTab
import com.pureplus.delivery.ui.theme.PpColor

/**
 * The **Run tab host**. Check-in is no longer a gate to the whole app — the bottom nav is always
 * visible, so this tab simply shows the right thing for the driver's state:
 *  - shift open  → [RouteHomeScreen] (today's run)
 *  - off shift   → [CheckInScreen] (check-in flow / "shift completed" state), bottom nav still shown
 *
 * It owns the [CheckInViewModel] so a successful check-in flips this tab to the run automatically (and
 * fires [onShiftOpen] to start live-location reporting). Earnings/Me/Stops remain reachable throughout.
 */
@Composable
fun RunTabScreen(
    onTab: (NavTab) -> Unit,
    onStartLoading: () -> Unit,
    onOpenStops: () -> Unit,
    onOpenStop: (Long) -> Unit,
    onCloseOut: () -> Unit,
    onEndShift: () -> Unit,
    onLogout: () -> Unit,
    onShiftOpen: () -> Unit,
    checkInViewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory),
) {
    val ci by checkInViewModel.state.collectAsStateWithLifecycle()
    val onShift = ci.checkedIn || ci.alreadyCheckedIn

    LaunchedEffect(onShift) { if (onShift) onShiftOpen() }

    when {
        !ci.statusResolved ->
            Box(Modifier.fillMaxSize().background(PpColor.Canvas)) { LoadingState() }
        onShift -> RouteHomeScreen(
            onTab = onTab,
            onStartLoading = onStartLoading,
            onOpenStops = onOpenStops,
            onOpenStop = onOpenStop,
            onCloseOut = onCloseOut,
            onEndShift = onEndShift,
            onLogout = onLogout,
        )
        else -> CheckInScreen(
            onTab = onTab,
            onLogout = onLogout,
            viewModel = checkInViewModel,
        )
    }
}
