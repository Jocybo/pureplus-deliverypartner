package com.pureplus.delivery.data.repository

import com.google.gson.Gson
import com.pureplus.delivery.data.remote.ApiResponse
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/** A tiny Result type so ViewModels branch on Success/Failure without try/catch everywhere. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(
        val message: String,
        val offline: Boolean = false,
        /** the token was rejected (HTTP 401) — the session is being torn down globally. */
        val unauthorized: Boolean = false,
    ) : ApiResult<Nothing>
}

/** True when the failure was a lost connection (drives the "queued offline" UX, never a red error). */
val ApiResult<*>.isOffline: Boolean get() = this is ApiResult.Failure && offline

private val errorGson = Gson()

/**
 * Runs an enveloped call and unwraps `data`. Every failure carries a **plain-English message** the
 * driver can act on — the server's own message when it sends one, otherwise a friendly mapping of the
 * status code. Raw "HTTP 400" strings are never surfaced. A 401 also tears the session down globally
 * (see [SessionManager]). Network drops / timeouts become an offline Failure (not a crash).
 */
suspend fun <T> apiCall(block: suspend () -> Response<ApiResponse<T>>): ApiResult<T> = try {
    val resp = block()
    val body = resp.body()
    when {
        resp.isSuccessful && body?.success == true && body.data != null ->
            ApiResult.Success(body.data as T)
        resp.isSuccessful && body?.success == true && body.data == null ->
            // Success with no payload. Return a typed null, NOT Unit: for a Unit-typed call
            // (e.g. ping) the data is ignored, while for a nullable read (an unassigned vehicle,
            // no active shift, …) callers fall back via `?: current`. Fabricating a `Unit` here
            // erases through the generic `as?` in ViewModels and only blows up as a
            // ClassCastException when assigned to the real field (kotlin.Unit → DriverVehicle).
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(null as T)
        else -> {
            // A 401 means the token is dead — bounce to login everywhere, don't strand the screen.
            if (resp.code() == 401) SessionManager.notifyExpired()
            // On a non-2xx the server's message lives in errorBody, not body — read both.
            val serverMsg = body?.message?.takeIf { it.isNotBlank() }
                ?: parseErrorMessage(resp.errorBody()?.string())
            ApiResult.Failure(
                message = friendlyMessage(resp.code(), serverMsg),
                unauthorized = resp.code() == 401,
            )
        }
    }
} catch (e: SocketTimeoutException) {
    ApiResult.Failure("The server took too long to respond. Please try again.", offline = true)
} catch (e: IOException) {
    ApiResult.Failure("You're offline — check your connection and we'll retry.", offline = true)
} catch (e: Exception) {
    ApiResult.Failure("Something went wrong. Please try again.")
}

/** Pull the `message` out of an enveloped `{success,data,message}` error body, if present. */
private fun parseErrorMessage(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        errorGson.fromJson(raw, ApiResponse::class.java)?.message?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

/**
 * Turn a status code into something a delivery partner can understand. A meaningful server message
 * always wins; otherwise we map the code — and we never print the number itself.
 */
private fun friendlyMessage(code: Int, serverMessage: String?): String {
    if (!serverMessage.isNullOrBlank()) return serverMessage
    return when (code) {
        400 -> "Some details didn't look right. Please check and try again."
        401 -> "Your session expired. Signing you out…"
        403 -> "You don't have access to this — it may belong to another store or driver."
        404 -> "We couldn't find this. It may have been updated or removed."
        408 -> "The request timed out. Please try again."
        409 -> "This was just updated elsewhere. Refresh and try again."
        422 -> "Some details couldn't be processed. Please review and retry."
        429 -> "Too many attempts. Please wait a moment and try again."
        in 500..599 -> "The server ran into a problem. Please try again shortly."
        else -> "Something went wrong. Please try again."
    }
}
