package com.pureplus.delivery.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/**
 * "One decision per screen": primary actions fill the width, are 56dp tall, and never move.
 * A single tappable is never below 48dp.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = PpColor.Brand700,
    content: Color = PpColor.White,
    height: Int = 56,
) {
    val isEnabled = enabled && !loading
    val bg = if (isEnabled) container else PpColor.DisabledBg
    val fg = if (isEnabled) content else PpColor.DisabledFg
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (isEnabled) Modifier.clickableNoRipple(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
        } else {
            Text(text, style = PpType.button, color = fg, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** Outlined secondary action (brand or danger border). */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = PpColor.Brand700,
    contentColor: Color = PpColor.Brand700,
    height: Int = 52,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PpColor.White)
            .border(BorderStroke(2.dp, if (enabled) borderColor else PpColor.Line), RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PpType.button, color = if (enabled) contentColor else PpColor.DisabledFg)
    }
}

/**
 * A compact outlined pill with a logout icon + label — the top-right action on the dark headers
 * (dashboard / check-in). Sits on a dark background by default (white ink).
 */
@Composable
fun LogoutIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PpColor.White,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.45f)), RoundedCornerShape(999.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Logout,
            contentDescription = "Log out",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text("Log out", style = PpType.bodySm.copy(color = tint))
    }
}

/** A quiet, centered text link ("Back to stop list", "Request manual override"). */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PpColor.Brand700,
) {
    Text(
        text,
        style = PpType.bodyStrong.copy(color = color),
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 6.dp),
    )
}
