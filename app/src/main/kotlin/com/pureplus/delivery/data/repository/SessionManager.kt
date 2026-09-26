package com.pureplus.delivery.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A single app-wide signal for "the staff session is no longer valid" (any 401 from the API).
 *
 * A dead token can't be recovered, so we don't leave the driver stranded on a screen with a
 * "session expired" note and no way forward — [apiCall] fires [notifyExpired] and the nav graph
 * (see `AppNavGraph`) clears the session and bounces straight to Login. This is the automatic
 * counterpart to the manual "Log out" action.
 */
object SessionManager {
    // extraBufferCapacity = 1 so tryEmit never drops the event even with no active collector yet.
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired = _expired.asSharedFlow()

    fun notifyExpired() {
        _expired.tryEmit(Unit)
    }
}
