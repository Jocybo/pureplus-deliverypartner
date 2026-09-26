package com.pureplus.delivery

import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.ui.closeout.CanRow
import com.pureplus.delivery.ui.delivery.DoorLine
import com.pureplus.delivery.ui.stops.isFailed
import com.pureplus.delivery.ui.stops.isPostpaid
import com.pureplus.delivery.util.isReusableProduct
import com.pureplus.delivery.util.money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic unit tests — the money math and reconciliation rules a driver's pay + the company's
 * cash depend on. Run: ./gradlew testDebugUnitTest
 */
class DeliveryLogicTest {

    // ── money() · ₹ with Indian grouping, whole rupees ──────────────────────
    @Test fun money_basic() {
        assertEquals("₹950", money(950.0))
        assertEquals("₹1,240", money(1240.0))
        assertEquals("₹0", money(0.0))
    }

    @Test fun money_indian_grouping() {
        assertEquals("₹12,34,567", money(1234567.0))   // last 3, then pairs
        assertEquals("₹18,940", money(18940.0))
    }

    @Test fun money_signed_for_receipt() {
        assertEquals("+ ₹200", money(200.0, signed = true))   // deposit added
        assertEquals("− ₹30", money(-30.0, signed = true))    // wallet credit
        assertEquals("₹30", money(30.0, signed = false))
    }

    @Test fun money_rounds_paise() {
        assertEquals("₹951", money(950.6))
    }

    // ── isReusableProduct() · empties expected only for reusable cans ───────
    @Test fun reusable_detection() {
        assertTrue(isReusableProduct("20L can"))
        assertTrue(isReusableProduct("10L Can (refill)"))
        assertTrue(isReusableProduct("Bubble Top jar"))
        assertFalse(isReusableProduct("1L pack"))
        assertFalse(isReusableProduct("500ml bottle"))
        assertFalse(isReusableProduct(null))
    }

    // ── DoorLine · at-the-door line math (DP-08) ────────────────────────────
    @Test fun doorline_shortfall_only_for_reusable() {
        val reusableShort = DoorLine(10, "20L can", 2, 80.0, reusable = true, deliveredQty = 2, emptiesCollected = 1)
        assertEquals(1, reusableShort.shortfall)          // 1 empty short → 1 fresh deposit
        assertEquals(160.0, reusableShort.lineTotal, 0.0) // 2 × ₹80

        val reusableAllReturned = reusableShort.copy(emptiesCollected = 2)
        assertEquals(0, reusableAllReturned.shortfall)

        val disposable = DoorLine(11, "1L pack", 4, 20.0, reusable = false, deliveredQty = 4, emptiesCollected = 0)
        assertEquals(0, disposable.shortfall)             // never asks for empties
    }

    // ── CanRow · shift-end can reconciliation (DP-15) ───────────────────────
    @Test fun canrow_balances() {
        // loaded 24 = delivered 21 + fulls back 3 + breakage 0  → balanced
        val balanced = CanRow(productId = 10, loadedOut = 24, delivered = 21, fullsReturned = 3, emptiesCollected = 17, breakageLoss = 0)
        assertEquals(0, balanced.variance)
    }

    @Test fun canrow_shrinkage_surfaces() {
        // loaded 24, delivered 21, only 1 full back, no breakage → 2 unaccounted
        val short = CanRow(productId = 10, loadedOut = 24, delivered = 21, fullsReturned = 1, emptiesCollected = 17, breakageLoss = 0)
        assertEquals(2, short.variance)
    }

    // ── Stop.isPostpaid / isFailed · never collect cash on a subscription ───
    @Test fun stop_payment_classification() {
        assertTrue(Stop(orderId = 1, paymentMode = "POSTPAID").isPostpaid)
        assertTrue(Stop(orderId = 2, paymentMode = "LEDGERED").isPostpaid)
        assertTrue(Stop(orderId = 3, paymentMode = "COD", paymentTypeId = 2).isPostpaid)
        assertFalse(Stop(orderId = 4, paymentMode = "COD", paymentTypeId = 1).isPostpaid)
        assertTrue(Stop(orderId = 5, status = "FAILED").isFailed)
        assertFalse(Stop(orderId = 6, status = "OUT_FOR_DELIVERY").isFailed)
    }
}
