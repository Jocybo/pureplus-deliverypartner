package com.pureplus.delivery.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** DP-07: hand off to the device's map app with the destination pre-filled. */
fun openMaps(context: Context, lat: Double?, lng: Double?, label: String?) {
    val query = if (lat != null && lng != null) {
        "$lat,$lng" + (label?.let { "(${Uri.encode(it)})" } ?: "")
    } else {
        Uri.encode(label ?: return)
    }
    val uri = Uri.parse("geo:0,0?q=$query")
    // Prefer Google Maps; fall back to any geo handler (the <queries> manifest entry makes both visible).
    val gmaps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    val generic = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(
            if (gmaps.resolveActivity(context.packageManager) != null) gmaps else generic,
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No map app found", Toast.LENGTH_SHORT).show()
    }
}

/** DP-06: dial the (masked) customer number via the phone app. */
fun dialPhone(context: Context, number: String?) {
    if (number.isNullOrBlank()) {
        Toast.makeText(context, "No number on file", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
}

/**
 * DP-02: open this app's system settings page so the driver can grant a permission they previously
 * blocked ("Don't ask again") — the only recovery path once Android stops showing the prompt.
 */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
