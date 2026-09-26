package com.pureplus.delivery.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

private val FieldBg = Color(0xFF152B3E)
private val FieldBorder = Color(0xFF2B4459)
private val Muted = Color(0xFF94AFC2)

/** DP-01 · Login. Dark screen (per the design) — the only dark screen; the rest are light-first. */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PpColor.Ink900)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(PpColor.Brand500),
            contentAlignment = Alignment.Center,
        ) {
            Text("P+", style = PpType.cardTitle.copy(color = PpColor.Ink900))
        }
        Text(
            "PurePlus\nDelivery Partner",
            style = PpType.screenTitle.copy(color = PpColor.White),
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Sign in with the ID your store admin gave you. Accounts are not self-registered.",
            style = PpType.body.copy(color = Muted),
        )

        if (state.step == LoginStep.Phone) {
            DarkField(
                label = "Registered mobile number",
                value = state.mobile,
                onChange = viewModel::onMobile,
                keyboardType = KeyboardType.Phone,
                prefix = "+91",
                modifier = Modifier.padding(top = 16.dp),
            )
            PrimaryButton(
                text = "Send OTP",
                onClick = viewModel::sendOtp,
                enabled = state.canSendOtp,
                loading = state.loading,
                container = PpColor.Brand500,
                content = PpColor.Ink900,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                "Verifying +91 ${state.mobile}",
                style = PpType.bodyStrong.copy(color = PpColor.White),
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Change number",
                style = PpType.bodyStrong.copy(color = PpColor.Brand500),
                modifier = Modifier.clickableNoRipple(viewModel::changeNumber),
            )
            DarkField(
                label = "Enter OTP",
                value = state.otp,
                onChange = viewModel::onOtp,
                keyboardType = KeyboardType.NumberPassword,
                modifier = Modifier.padding(top = 8.dp),
            )
            PrimaryButton(
                text = "Verify & sign in",
                onClick = viewModel::verify,
                enabled = state.canVerify,
                loading = state.loading,
                container = PpColor.Brand500,
                content = PpColor.Ink900,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (state.canResend) "Resend OTP" else "Resend in ${state.resendIn}s",
                style = PpType.bodySm.copy(color = if (state.canResend) PpColor.Brand500 else Muted),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (state.canResend) Modifier.clickableNoRipple(viewModel::resend) else Modifier),
            )
            if (state.demoOtp != null) {
                NotePanel("Demo OTP (SMS not live yet): ${state.demoOtp}", tone = Tone.Info)
            }
        }

        if (state.error != null) {
            NotePanel(state.error!!, tone = Tone.Danger)
        }

        Text(
            "Sign in with your store-registered number. No password — a one-time code is sent to your phone.",
            style = PpType.bodySm.copy(color = Muted),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun DarkField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    visual: VisualTransformation = VisualTransformation.None,
    prefix: String? = null,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = PpType.label.copy(color = Muted))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = visual,
            prefix = prefix?.let { { Text("$it ", style = PpType.bodyStrong.copy(color = Muted)) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = if (trailing != null && onTrailing != null) {
                {
                    Text(
                        trailing,
                        style = PpType.label.copy(color = PpColor.Brand500),
                        modifier = Modifier.clickableNoRipple(onTrailing).padding(end = 12.dp),
                    )
                }
            } else null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = FieldBg,
                unfocusedContainerColor = FieldBg,
                focusedTextColor = PpColor.White,
                unfocusedTextColor = PpColor.White,
                cursorColor = PpColor.Brand500,
                focusedIndicatorColor = FieldBorder,
                unfocusedIndicatorColor = FieldBorder,
            ),
        )
    }
}
