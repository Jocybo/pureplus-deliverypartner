package com.pureplus.delivery.ui.stops

import com.pureplus.delivery.data.remote.Stop

/**
 * Pure (Compose-free) helpers over a [Stop] — kept in their own file so they're unit-testable on the
 * JVM without pulling in Compose. Same package as StopUi.kt, so no imports change at call sites.
 */

/** Postpaid stops never collect cash — visually distinct so a driver never asks a subscription customer to pay. */
val Stop.isPostpaid: Boolean
    get() = paymentMode.contains("POST", true) || paymentMode.contains("LEDGER", true) || paymentTypeId == 2

val Stop.isDelivered: Boolean
    get() = status.equals("DELIVERED", true) || status.equals("DELIVERED_PAYMENT_PENDING", true)

val Stop.isFailed: Boolean get() = status.equals("FAILED", true)

fun Stop.addressText(): String =
    listOfNotNull(addressLine1, addressLine2, city).joinToString(", ").ifBlank { pincode ?: "" }
