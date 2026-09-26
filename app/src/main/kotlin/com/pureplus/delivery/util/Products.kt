package com.pureplus.delivery.util

/**
 * Heuristic: is this product a reusable can (deposit-bearing, empty expected back) vs a disposable pack?
 * The stop-item payload has no `isReusable` flag yet, so we infer from the name. Replace with a real
 * flag once the backend adds one (see STATUS.md · missing APIs).
 */
fun isReusableProduct(name: String?): Boolean {
    val n = name?.lowercase() ?: return false
    return n.contains("can") || n.contains("20l") || n.contains("10l") ||
        n.contains("jar") || n.contains("bubble") || n.contains("refill")
}
