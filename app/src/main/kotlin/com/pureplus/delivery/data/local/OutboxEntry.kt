package com.pureplus.delivery.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline outbox — every write that must survive network loss.
 * A WorkManager SyncWorker flushes these in creation-order when connectivity returns.
 *
 * Operations that are safe to enqueue here:
 *   DELIVER, CONFIRM_PAYMENT, EXCEPTION, CAN_LINES, CASH_TALLY, CLOSE_SESSION
 *
 * The idempotencyKey on DELIVER/CONFIRM_PAYMENT prevents double-charges on retry.
 */
@Entity(tableName = "outbox")
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),

    /** Operation type (see OutboxOperation). */
    val operation: String,

    /** URL to POST/PUT. */
    val url: String,

    /** JSON-serialised request body. */
    val bodyJson: String,

    /** HTTP method: POST | PUT. */
    val method: String = "POST",

    val status: String = OutboxStatus.PENDING,

    /** Non-null for DELIVER and CONFIRM_PAYMENT — prevents double charges on retry. */
    val idempotencyKey: String? = null,

    val retryCount: Int = 0,
    val lastError: String? = null,
)

object OutboxStatus {
    const val PENDING = "PENDING"
    const val SYNCED  = "SYNCED"
    const val FAILED  = "FAILED"   // retried 3× and gave up — needs manual review
}

object OutboxOperation {
    const val DELIVER         = "DELIVER"
    const val CONFIRM_PAYMENT = "CONFIRM_PAYMENT"
    const val EXCEPTION       = "EXCEPTION"
    const val CAN_LINES       = "CAN_LINES"
    const val CASH_TALLY      = "CASH_TALLY"
    const val CLOSE_SESSION   = "CLOSE_SESSION"
}
