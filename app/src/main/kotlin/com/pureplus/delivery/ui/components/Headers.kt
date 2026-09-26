package com.pureplus.delivery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

private val OnDarkMuted = Color(0xFF94AFC2)

/** Ink-900 header block with an uppercase eyebrow and a big title (route home, check-in, earnings…). */
@Composable
fun DarkHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PpColor.Ink900)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // weight(1f) so a long title can't push the trailing action (e.g. "Log out") off-screen.
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                if (eyebrow != null) {
                    Text(eyebrow.uppercase(), style = PpType.label.copy(color = PpColor.BrandOnDark))
                }
                Text(
                    title,
                    style = PpType.screenTitle.copy(color = PpColor.White),
                    modifier = Modifier.padding(top = if (eyebrow != null) 8.dp else 0.dp),
                )
                if (subtitle != null) {
                    Text(subtitle, style = PpType.body.copy(color = OnDarkMuted), modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (trailing != null) trailing()
        }
        if (extra != null) extra()
    }
}

/** Light top bar with a back affordance and title — used on pushed detail screens. */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backLabel: String = "Back",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PpColor.White)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickableNoRipple(onBack),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = backLabel,
                tint = PpColor.Brand700,
                modifier = Modifier.size(18.dp),
            )
            Text(backLabel, style = PpType.bodyStrong.copy(color = PpColor.Brand700), modifier = Modifier.padding(start = 6.dp))
        }
//        Text(title, style = PpType.screenTitle.copy(color = PpColor.Ink900), modifier = Modifier.padding(top = 8.dp))
//        if (subtitle != null) {
//            Text(subtitle, style = PpType.bodySm.copy(color = PpColor.Ink500), modifier = Modifier.padding(top = 4.dp))
//        }
    }
}
