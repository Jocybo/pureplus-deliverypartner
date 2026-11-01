package com.pureplus.delivery.ui.stops

import com.pureplus.delivery.data.remote.Stop

/**
 * Pure (Compose-free) helpers over a [Stop] — kept in their own file so they're unit-testable on the
 * JVM without pulling in Compose. Same package as StopUi.kt, so no imports change at call sites.
 */

/**
 * Postpaid stops never collect cash — visually distinct so a driver never asks a subscription customer to pay.
 *
 * Detected from [Stop.paymentMode] ONLY. NOTE: `paymentTypeId` is NOT a postpaid signal — the backend uses
 * `payment_type = 1` for COD and `2` for everything online (DriverStopService maps 2 → "ONLINE"), so an
 * ordinary prepaid/online order carries `paymentTypeId == 2`. Keying off that made every online stop show
 * "Postpaid · collect nothing", which is wrong — those orders still collect at the door.
 */
val Stop.isPostpaid: Boolean
    get() = paymentMode.contains("POST", true) || paymentMode.contains("LEDGER", true)

/**
 * The amount the driver collects at the door — products + refundable deposit. Prefers the backend's
 * [Stop.collectAtDoor]; falls back to [Stop.totalAmount] when a lighter endpoint didn't include it.
 * NOTE: the stop LIST projection (DriverStopRepository) currently returns only total_amount, so on the
 * dashboard / list this falls back until the backend adds collectAtDoor to that query. The stop DETAIL
 * endpoint returns the real value.
 */
val Stop.amountToCollect: Double
    get() = if (collectAtDoor > 0.0) collectAtDoor else totalAmount

val Stop.isDelivered: Boolean
    get() = status.equals("DELIVERED", true) || status.equals("DELIVERED_PAYMENT_PENDING", true)

val Stop.isFailed: Boolean get() = status.equals("FAILED", true)

/** Canonical status with separators/case stripped, matching the backend's OrderLifecycleRules.Normalize. */
private fun Stop.normStatus(): String = status.orEmpty().uppercase().replace(Regex("[^A-Z0-9]"), "")

/** Still ASSIGNED — must be taken OUT_FOR_DELIVERY (DP "Start delivery") before the door flow. */
val Stop.isAssigned: Boolean get() = normStatus() == "ASSIGNED"

/** Already dispatched — the driver can go straight to the doorstep flow. */
val Stop.isOutForDelivery: Boolean get() = normStatus() == "OUTFORDELIVERY" || normStatus() == "OFD"

fun Stop.addressText(): String =
    listOfNotNull(addressLine1, addressLine2, city).joinToString(", ").ifBlank { pincode ?: "" }
