package com.pureplus.delivery.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.AuthRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginStep { Phone, Otp }

data class LoginState(
    val step: LoginStep = LoginStep.Phone,
    val mobile: String = "",
    val otp: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** demo OTP echoed by the backend until SMS (MSG91) is wired — shown as a hint. */
    val demoOtp: String? = null,
    val resendIn: Int = 0,
    val success: Boolean = false,
) {
    val canSendOtp: Boolean get() = mobile.length == 10 && !loading
    val canVerify: Boolean get() = otp.length == OTP_LEN && !loading
    val canResend: Boolean get() = resendIn == 0 && !loading
}

private const val OTP_LEN = 4
private const val RESEND_SECONDS = 30

/** DP-01 · Driver login via OTP to the registered mobile (send → verify). */
class LoginViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    fun onMobile(v: String) = _state.update { it.copy(mobile = v.filter(Char::isDigit).take(10), error = null) }
    fun onOtp(v: String) = _state.update { it.copy(otp = v.filter(Char::isDigit).take(OTP_LEN), error = null) }

    fun sendOtp() {
        if (!_state.value.canSendOtp) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = repo.sendOtp(_state.value.mobile)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, step = LoginStep.Otp, demoOtp = r.data.otp, otp = "") }
                    startResendCountdown()
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun resend() { if (_state.value.canResend) sendOtp() }

    fun verify() {
        if (!_state.value.canVerify) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = repo.verifyOtp(_state.value.mobile, _state.value.otp)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, success = true) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun changeNumber() = _state.update { it.copy(step = LoginStep.Phone, otp = "", error = null, demoOtp = null) }

    private fun startResendCountdown() {
        viewModelScope.launch {
            _state.update { it.copy(resendIn = RESEND_SECONDS) }
            while (_state.value.resendIn > 0) {
                delay(1000)
                _state.update { it.copy(resendIn = (it.resendIn - 1).coerceAtLeast(0)) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { LoginViewModel(Deps.authRepository) } }
    }
}
