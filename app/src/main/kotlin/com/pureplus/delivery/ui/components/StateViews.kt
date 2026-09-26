package com.pureplus.delivery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PpColor.Brand700)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Something went wrong", style = PpType.cardTitle, color = PpColor.Ink900)
        Text(message, style = PpType.body.copy(color = PpColor.Ink500), textAlign = TextAlign.Center)
        if (onRetry != null) {
            PrimaryButton("Try again", onRetry, modifier = Modifier.fillMaxWidth(0.6f))
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = PpType.cardTitle, color = PpColor.Ink900, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, style = PpType.body.copy(color = PpColor.Ink500), textAlign = TextAlign.Center)
        }
    }
}

/** Uppercase section label used above cards and inside them. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = PpColor.Ink500) {
    Text(text.uppercase(), style = PpType.label.copy(color = color), modifier = modifier)
}

/** A label/value row inside a card (e.g. "Amount payable" · ₹860). */
@Composable
fun LabeledRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle = PpType.mono,
    labelColor: androidx.compose.ui.graphics.Color = PpColor.Ink700,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PpType.bodyStrong.copy(color = labelColor))
        Text(value, style = valueStyle)
    }
}
