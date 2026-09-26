package com.pureplus.delivery.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pureplus.delivery.data.local.TokenStore
import com.pureplus.delivery.data.remote.LoginRequest
import com.pureplus.delivery.data.remote.LoginResponse
import com.pureplus.delivery.data.remote.RetrofitClient
import com.pureplus.delivery.data.remote.SendOtpRequest
import com.pureplus.delivery.data.remote.SendOtpResponse
import com.pureplus.delivery.data.remote.VerifyOtpRequest
import com.pureplus.delivery.data.remote.VerifyOtpResponse
import retrofit2.Response
import java.io.IOException

/**
 * DP-01 login. **Primary path is OTP** (`send-otp` → `verify-otp`) — it works for any onboarded,
 * active delivery partner by their registered mobile, with no password (drivers are onboarded
 * without one). A store-scoped staff JWT is persisted in [TokenStore] on success. The password
 * [login] below is kept only as an optional fallback and isn't used by the UI.
 */
class AuthRepository(context: Context) {
    private val api = RetrofitClient.apiService
    private val tokenStore = TokenStore(context)
    private val gson = Gson()

    /** Request an OTP for a 10-digit mobile. Returns the demo OTP (until SMS is wired), if the API echoes it. */
    suspend fun sendOtp(mobile: String): ApiResult<SendOtpResponse> = try {
        val resp = api.sendOtp(SendOtpRequest(mobile.trim()))
        if (resp.isSuccessful && resp.body() != null) {
            ApiResult.Success(resp.body()!!)
        } else {
            val err = parseError(resp.errorBody()?.string(), SendOtpResponse::class.java)
            ApiResult.Failure(err?.message ?: "Couldn't send OTP. Please try again.")
        }
    } catch (e: IOException) {
        ApiResult.Failure("Can't reach the server. Check your connection.", offline = true)
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Couldn't send OTP.")
    }

    /** Verify the OTP; on success persist the staff session. */
    suspend fun verifyOtp(mobile: String, otp: String): ApiResult<VerifyOtpResponse> = try {
        val resp = api.verifyOtp(VerifyOtpRequest(mobile.trim(), otp.trim()))
        val body = resp.body()
        if (resp.isSuccessful && body?.success == true && !body.token.isNullOrBlank()) {
            tokenStore.saveSession(body.token, body.employeeId.toLong(), body.storeId, body.name, body.roleId.toString())
            ApiResult.Success(body)
        } else {
            val err = body?.message ?: parseError(resp.errorBody()?.string(), VerifyOtpResponse::class.java)?.message
            ApiResult.Failure(err ?: "Invalid OTP.")
        }
    } catch (e: IOException) {
        ApiResult.Failure("Can't reach the server. Check your connection.", offline = true)
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Verification failed.")
    }

    private fun <T> parseError(raw: String?, type: Class<T>): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching { gson.fromJson(raw, type) }.getOrNull()
    }

    suspend fun login(username: String, password: String): ApiResult<LoginResponse> = try {
        val resp = api.login(LoginRequest(username.trim(), password))
        val body = resp.body()
        if (resp.isSuccessful && !body?.token.isNullOrBlank()) {
            tokenStore.saveSession(
                token = body!!.token!!,
                employeeId = body.employeeId.toLong(),
                storeId = body.storeId,
                fullName = body.name,
                role = body.roleId.toString(),
            )
            ApiResult.Success(body)
        } else {
            // 401/500 bodies are a bare { message }; Retrofit parks them in errorBody.
            ApiResult.Failure(errorMessage(resp) ?: "Invalid credentials.")
        }
    } catch (e: IOException) {
        ApiResult.Failure("Can't reach the server. Check your connection and try again.", offline = true)
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Login failed.")
    }

    suspend fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    suspend fun logout() = tokenStore.clearSession()

    suspend fun driverName(): String = tokenStore.getFullName()

    private fun errorMessage(resp: retrofit2.Response<LoginResponse>): String? {
        resp.body()?.message?.let { return it }
        val raw = resp.errorBody()?.string() ?: return null
        return try {
            gson.fromJson(raw, LoginResponse::class.java)?.message
        } catch (e: JsonSyntaxException) {
            null
        }
    }
}
