package com.pureplus.delivery.data.repository

import android.content.Context
import com.pureplus.delivery.data.local.OutboxOperation
import com.pureplus.delivery.data.remote.ApiConstants
import com.pureplus.delivery.data.remote.CloseSessionRequest
import com.pureplus.delivery.data.remote.LoadOutLine
import com.pureplus.delivery.data.remote.OpenSessionRequest
import com.pureplus.delivery.data.remote.ReconciliationSession
import com.pureplus.delivery.data.remote.RecordLoadOutRequest
import com.pureplus.delivery.data.remote.RetrofitClient
import com.pureplus.delivery.data.remote.SetCanLineRequest
import com.pureplus.delivery.data.remote.SetCashRequest
import com.pureplus.delivery.util.todayIso

/**
 * Load-out (DP-04) + the close-out loop (DP-15 cans → DP-16 cash → DP-22 close).
 * `close` succeeds only when both loops balance — that IS the DP-22 check-out gate.
 * Per-line writes queue offline; open/get need to be online (they return authoritative state).
 */
class ReconciliationRepository(context: Context) {
    private val api = RetrofitClient.apiService
    private val outbox = OutboxRepository(context)

    // DP-04 — records the baseline and opens the session if one doesn't exist.
    suspend fun loadOut(lines: List<LoadOutLine>, shiftDate: String = todayIso()): ApiResult<ReconciliationSession> =
        apiCall { api.loadOut(RecordLoadOutRequest(shiftDate, lines)) }

    suspend fun openSession(shiftDate: String = todayIso()): ApiResult<ReconciliationSession> =
        apiCall { api.openSession(OpenSessionRequest(shiftDate)) }

    suspend fun getSession(sessionId: Long): ApiResult<ReconciliationSession> =
        apiCall { api.getSession(ApiConstants.reconSession(sessionId)) }

    // DP-15 — one line per product.
    suspend fun setCanLine(sessionId: Long, req: SetCanLineRequest): ApiResult<ReconciliationSession> {
        val url = ApiConstants.reconCanLines(sessionId)
        return when (val r = apiCall { api.setCanLine(url, req) }) {
            is ApiResult.Success -> r
            is ApiResult.Failure -> {
                if (r.offline) outbox.enqueue(OutboxOperation.CAN_LINES, url, req, method = "PUT")
                r
            }
        }
    }

    // DP-16 — cash counts + variance.
    suspend fun setCash(sessionId: Long, req: SetCashRequest): ApiResult<ReconciliationSession> {
        val url = ApiConstants.reconCash(sessionId)
        return when (val r = apiCall { api.setCash(url, req) }) {
            is ApiResult.Success -> r
            is ApiResult.Failure -> {
                if (r.offline) outbox.enqueue(OutboxOperation.CASH_TALLY, url, req, method = "PUT")
                r
            }
        }
    }

    // DP-22 — the both-loops gate (legacy pre-ShiftV2 close).
    suspend fun close(sessionId: Long, closeNote: String? = null): ApiResult<ReconciliationSession> =
        apiCall { api.closeSession(ApiConstants.reconClose(sessionId), CloseSessionRequest(closeNote)) }

    /**
     * ShiftV2 (Phase 3) — the driver's TERMINAL write: OPEN → DRIVER_DECLARED, then the session is
     * read-only for the driver and awaits store verification. No body. Because this is the last thing a
     * driver does on a shift, it must survive a network drop: on an offline failure it is queued to the
     * outbox (DECLARE_SESSION) so it syncs on reconnect — mirroring the CLOSE_SESSION path. The transition
     * is idempotent server-side, so a replay is safe.
     */
    suspend fun declare(sessionId: Long): ApiResult<ReconciliationSession> {
        val url = ApiConstants.reconDeclare(sessionId)
        return when (val r = apiCall { api.declareSession(url) }) {
            is ApiResult.Success -> r
            is ApiResult.Failure -> {
                if (r.offline) outbox.enqueue(OutboxOperation.DECLARE_SESSION, url, body = null)
                r
            }
        }
    }
}
