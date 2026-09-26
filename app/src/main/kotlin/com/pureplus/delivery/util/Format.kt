package com.pureplus.delivery.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * ₹ with Indian grouping (₹1,240). Whole rupees are shown without paise (the design never shows paise).
 * [signed] forces a leading + / − (used in the receipt for additions/credits).
 */
fun money(amount: Double, signed: Boolean = false): String {
    val whole = abs(amount).roundToLong()
    val grouped = groupIndian(whole)
    val sign = when {
        !signed -> ""
        amount < 0 -> "− "
        else -> "+ "
    }
    return "$sign₹$grouped"
}

fun money(amount: java.math.BigDecimal?, signed: Boolean = false): String =
    money(amount?.toDouble() ?: 0.0, signed)

/** 1234567 -> "12,34,567" (Indian grouping: last 3 digits, then pairs). */
private fun groupIndian(value: Long): String {
    val s = value.toString()
    if (s.length <= 3) return s
    val last3 = s.substring(s.length - 3)
    val rest = s.substring(0, s.length - 3)
    val sb = StringBuilder()
    var count = 0
    for (i in rest.length - 1 downTo 0) {
        sb.append(rest[i])
        count++
        if (count % 2 == 0 && i != 0) sb.append(',')
    }
    return sb.reverse().toString() + "," + last3
}

/** ISO-8601 (with or without offset) -> "8:30 AM". Returns the raw string if it can't be parsed. */
fun timeOfDay(iso: String?): String {
    val dt = parseDateTime(iso) ?: return iso.orEmpty()
    return dt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
}

/** ISO date -> "Mon 21 Sep". */
fun shortDate(iso: String?): String {
    val d = parseDate(iso) ?: return iso.orEmpty()
    return d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
}

/** Backend timestamps like "2026-09-21 03:57:22" (space-separated, no 'T', optional seconds). */
private val SPACE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]")

fun parseDateTime(iso: String?): LocalDateTime? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(iso) }
        .recoverCatching { LocalDateTime.parse(iso.trim(), SPACE_DATE_TIME) }
        .recoverCatching { LocalDate.parse(iso.take(10)).atStartOfDay() }
        .getOrNull()
}

fun parseDate(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
}

/** Today's shift date in the yyyy-MM-dd the backend expects. */
fun todayIso(): String = LocalDate.now().toString()
