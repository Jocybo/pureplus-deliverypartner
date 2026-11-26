package com.pureplus.delivery.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.CountStepper
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** "10 cans" / "1 can" / "10" (no uom). Pluralises the uom for a count ≠ 1. */
private fun qtyWithUom(n: Int, uom: String?): String {
    val u = uom?.trim()?.takeIf { it.isNotEmpty() } ?: return "$n"
    val unit = if (n == 1 || u.endsWith("s", ignoreCase = true)) u else "${u}s"
    return "$n $unit"
}

/** DP-04 · Loading confirmation. Sets the shift's reconciliation baseline. */
@Composable
fun LoadOutScreen(
    onBack: () -> Unit,
    onLoadedOut: () -> Unit,
    viewModel: LoadOutViewModel = viewModel(factory = LoadOutViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) { if (state.done) onLoadedOut() }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader(
            title = "Load out",
            onBack = onBack,
            backLabel = "Today's run",
            subtitle = "Confirm what's going on your vehicle — this sets your can baseline.",
        )

        if (state.loading) {
            Box(Modifier.weight(1f).fillMaxWidth()) { LoadingState() }
            return@Column
        }

        val totalCans = state.rows.sumOf { it.loaded }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Summary strip — how many products and cans are being loaded.
            if (state.rows.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PpColor.Brand50)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = PpColor.BrandInk, modifier = Modifier.width(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${state.rows.size} product${if (state.rows.size == 1) "" else "s"} · $totalCans can${if (totalCans == 1) "" else "s"} to load",
                        style = PpType.bodyStrong.copy(color = PpColor.BrandInk),
                    )
                }
            }

            state.rows.forEach { row ->
                PpCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            // Product name — the headline.
                            Text(row.productName, style = PpType.cardTitle)
                            Spacer(Modifier.height(4.dp))
                            // Required quantity, spelled out with the unit of measure.
                            Text(
                                "Required ${qtyWithUom(row.required, row.uom)}",
                                style = PpType.bodySm.copy(color = PpColor.Ink500),
                            )
                        }
                        CountStepper(
                            value = row.loaded,
                            onChange = { viewModel.setLoaded(row.productId, it) },
                            valueColor = if (row.short > 0) PpColor.Danger else PpColor.Ink900,
                        )
                    }

                    // Inline shortfall note on this product only.
                    if (row.short > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Short by ${qtyWithUom(row.short, row.uom)} — affected stops are flagged.",
                            style = PpType.bodySm.copy(color = PpColor.Danger),
                        )
                    }
                }
            }

            if (state.rows.isEmpty()) {
                NotePanel("No items to load for this shift yet.", tone = Tone.Neutral)
            }
            if (state.shortages.isNotEmpty()) {
                val n = state.shortages.sumOf { it.short }
                NotePanel(
                    "$n short across ${state.shortages.size} product(s). Affected stops are flagged now so you don't find out at the door — store admin is alerted before you leave.",
                    tone = Tone.Danger,
                )
            }
            if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                "Confirm loaded quantities",
                onClick = viewModel::confirm,
                loading = state.submitting,
                enabled = state.rows.isNotEmpty() && !state.submitting,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}
