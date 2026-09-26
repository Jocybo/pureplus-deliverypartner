package com.pureplus.delivery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.pureplus.delivery.ui.navigation.AppNavGraph
import com.pureplus.delivery.ui.theme.PurePlusDeliveryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On targetSdk 35 (Android 15) the bars are transparent and statusBarColor/navigationBarColor
        // are ignored — so we (1) draw a black background behind the inset padding via the Scaffold's
        // containerColor, and (2) force LIGHT (white) bar icons so they read on black.
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.isAppearanceLightStatusBars = false      // false → light icons
        bars.isAppearanceLightNavigationBars = false

        setContent {
            PurePlusDeliveryTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black,                     // the status/nav bar strips
                    contentWindowInsets = WindowInsets.safeDrawing,   // status + nav + cutout + keyboard
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        AppNavGraph()
                    }
                }
            }
        }
    }
}
