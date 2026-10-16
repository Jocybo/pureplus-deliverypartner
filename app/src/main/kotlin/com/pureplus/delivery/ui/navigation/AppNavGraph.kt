package com.pureplus.delivery.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pureplus.delivery.data.location.LocationService
import com.pureplus.delivery.data.repository.SessionManager
import com.pureplus.delivery.di.Deps
import com.pureplus.delivery.ui.auth.LoginScreen
import com.pureplus.delivery.ui.bulk.BulkScreen
import com.pureplus.delivery.ui.closeout.CanReturnScreen
import com.pureplus.delivery.ui.closeout.CashReconcileScreen
import com.pureplus.delivery.ui.closeout.CheckOutScreen
import com.pureplus.delivery.ui.components.NavTab
import com.pureplus.delivery.ui.delivery.DeliveryScreen
import com.pureplus.delivery.ui.delivery.UnableToDeliverScreen
import com.pureplus.delivery.ui.self.AttendanceDetailScreen
import com.pureplus.delivery.ui.self.AttendanceScreen
import com.pureplus.delivery.ui.self.DailyGoalScreen
import com.pureplus.delivery.ui.self.EarningsScreen
import com.pureplus.delivery.ui.self.HistoryScreen
import com.pureplus.delivery.ui.self.NotificationsScreen
import com.pureplus.delivery.ui.self.ProfileScreen
import com.pureplus.delivery.ui.shift.LoadOutScreen
import com.pureplus.delivery.ui.shift.RunTabScreen
import com.pureplus.delivery.ui.stops.StopDetailScreen
import com.pureplus.delivery.ui.stops.StopListScreen

object Routes {
    const val LOGIN = "login"
    const val CHECK_IN = "check_in"
    const val RUN = "run"
    const val STOPS = "stops"
    const val EARNINGS = "earnings"
    const val ME = "me"
    const val LOAD_OUT = "load_out"
    const val STOP_DETAIL = "stop_detail/{orderId}"
    const val DELIVER = "deliver/{orderId}"
    const val UNABLE = "unable/{orderId}"
    const val BULK = "bulk"
    const val CAN_RETURN = "can_return"
    const val CASH = "cash/{sessionId}"
    const val CHECK_OUT = "check_out/{sessionId}"
    const val END_SHIFT = "end_shift"   // direct check-out for a mistaken check-in (no run)
    const val DAILY_GOAL = "daily_goal"
    const val ATTENDANCE = "attendance"
    const val ATTENDANCE_DETAIL = "attendance_detail/{checkInId}"
    const val HISTORY = "history"
    const val NOTIFICATIONS = "notifications"

    fun attendanceDetail(checkInId: Long) = "attendance_detail/$checkInId"
    fun stopDetail(orderId: Long) = "stop_detail/$orderId"
    fun deliver(orderId: Long) = "deliver/$orderId"
    fun unable(orderId: Long) = "unable/$orderId"
    fun cash(sessionId: Long) = "cash/$sessionId"
    fun checkOut(sessionId: Long) = "check_out/$sessionId"

    fun tabRoute(tab: NavTab) = when (tab) {
        NavTab.Run -> RUN
        NavTab.Stops -> STOPS
        NavTab.Earnings -> EARNINGS
        NavTab.Me -> ME
    }
}

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        // The shell is home — the Run tab shows check-in or the run depending on shift state.
        start = if (Deps.tokenStore.isLoggedIn()) Routes.RUN else Routes.LOGIN
    }
    val startDestination = start ?: return
    val appContext = LocalContext.current.applicationContext

    // Switch bottom-nav tabs while preserving each tab's state.
    fun goTab(tab: NavTab) {
        navController.navigate(Routes.tabRoute(tab)) {
            popUpTo(Routes.RUN) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun toLoggedOut() {
        LocationService.stop(appContext)
        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }
    fun toStopsFresh() = navController.navigate(Routes.STOPS) {
        popUpTo(Routes.STOPS) { inclusive = true }
        launchSingleTop = true
    }

    // Any 401 from the API means the token is dead → clear the session and bounce to Login,
    // so the driver is never stranded on a "session expired" note with no way forward.
    LaunchedEffect(Unit) {
        SessionManager.expired.collect {
            Deps.authRepository.logout()
            toLoggedOut()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Routes.RUN) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }

        // ── Bottom-nav tabs ──────────────────────────────────────────────────
        // The Run tab hosts check-in (off shift) or the run (on shift); bottom nav is always shown.
        composable(Routes.RUN) {
            RunTabScreen(
                onTab = ::goTab,
                onStartLoading = { navController.navigate(Routes.LOAD_OUT) },
                onOpenStops = { goTab(NavTab.Stops) },
                onOpenStop = { navController.navigate(Routes.stopDetail(it)) },
                onCloseOut = { navController.navigate(Routes.CAN_RETURN) },
                onEndShift = { navController.navigate(Routes.END_SHIFT) },
                onLogout = { toLoggedOut() },
                onShiftOpen = { LocationService.start(appContext) }, // DP-07: report position while on shift
            )
        }
        composable(Routes.STOPS) {
            StopListScreen(
                onTab = ::goTab,
                onOpenStop = { navController.navigate(Routes.stopDetail(it)) },
                onCloseOut = { navController.navigate(Routes.CAN_RETURN) },
            )
        }
        composable(Routes.EARNINGS) {
            EarningsScreen(onTab = ::goTab, onOpenGoal = { navController.navigate(Routes.DAILY_GOAL) })
        }
        composable(Routes.ME) {
            ProfileScreen(
                onTab = ::goTab,
                onOpenAttendance = { navController.navigate(Routes.ATTENDANCE) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenBulk = { navController.navigate(Routes.BULK) },
                onLogout = { toLoggedOut() },
            )
        }

        // ── Shift open ───────────────────────────────────────────────────────
        composable(Routes.LOAD_OUT) {
            LoadOutScreen(
                onBack = { navController.popBackStack() },
                onLoadedOut = { toStopsFresh() },
            )
        }

        // ── The run ──────────────────────────────────────────────────────────
        composable(Routes.STOP_DETAIL, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
            val orderId = entry.arguments?.getLong("orderId") ?: return@composable
            StopDetailScreen(
                orderId = orderId,
                onDeliver = { navController.navigate(Routes.deliver(orderId)) },
                onUnable = { navController.navigate(Routes.unable(orderId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DELIVER, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
            val orderId = entry.arguments?.getLong("orderId") ?: return@composable
            DeliveryScreen(
                orderId = orderId,
                onDone = { toStopsFresh() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.UNABLE, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
            val orderId = entry.arguments?.getLong("orderId") ?: return@composable
            UnableToDeliverScreen(
                orderId = orderId,
                onDone = { toStopsFresh() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BULK) {
            BulkScreen(onBack = { navController.popBackStack() })
        }

        // ── Close out ────────────────────────────────────────────────────────
        composable(Routes.CAN_RETURN) {
            CanReturnScreen(onNext = { sessionId -> navController.navigate(Routes.cash(sessionId)) })
        }
        composable(Routes.CASH, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            CashReconcileScreen(sessionId = sessionId, onNext = { navController.navigate(Routes.checkOut(sessionId)) })
        }
        composable(Routes.CHECK_OUT, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            CheckOutScreen(
                sessionId = sessionId,
                onCheckedOut = {
                    LocationService.stop(appContext)   // DP-22: shift closed, stop reporting
                    navController.navigate(Routes.RUN) { popUpTo(0) { inclusive = true } }
                },
                onGoCloseOut = { navController.navigate(Routes.CAN_RETURN) },
            )
        }
        composable(Routes.END_SHIFT) {
            // Mistaken check-in → attendance-only check-out (no session to reconcile).
            CheckOutScreen(
                sessionId = null,
                onCheckedOut = {
                    LocationService.stop(appContext)
                    navController.navigate(Routes.RUN) { popUpTo(0) { inclusive = true } }
                },
                onGoCloseOut = { navController.navigate(Routes.CAN_RETURN) },
            )
        }

        // ── Self-service ─────────────────────────────────────────────────────
        composable(Routes.DAILY_GOAL) { DailyGoalScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ATTENDANCE) {
            AttendanceScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { navController.navigate(Routes.attendanceDetail(it)) },
            )
        }
        composable(Routes.ATTENDANCE_DETAIL, arguments = listOf(navArgument("checkInId") { type = NavType.LongType })) { entry ->
            val checkInId = entry.arguments?.getLong("checkInId") ?: return@composable
            AttendanceDetailScreen(checkInId = checkInId, onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) { HistoryScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { navController.popBackStack() }) }
    }
}
