package com.pureplus.delivery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpFont

enum class NavTab(val label: String, val icon: ImageVector) {
    Run("Run", Icons.Filled.LocalShipping),
    Stops("Stops", Icons.AutoMirrored.Filled.ListAlt),
    Earnings("Earnings", Icons.Filled.CurrencyRupee),
    Me("Me", Icons.Filled.Person),
}

/** The 4-tab bottom bar: Run · Stops · Earnings · Me. Active tab carries a top rail + brand tint. */
@Composable
fun BottomNavBar(current: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PpColor.White)
            .padding(bottom = 6.dp),
    ) {
        NavTab.entries.forEach { tab ->
            val active = tab == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) PpColor.Brand50 else PpColor.White)
                    .clickableNoRipple { onSelect(tab) }
                    .padding(top = 9.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (active) PpColor.Brand700 else PpColor.Ink500,
                    modifier = Modifier.height(20.dp),
                )
                Text(
                    tab.label,
                    fontFamily = PpFont.Ui,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (active) PpColor.Brand700 else PpColor.Ink500,
                )
            }
        }
    }
}
