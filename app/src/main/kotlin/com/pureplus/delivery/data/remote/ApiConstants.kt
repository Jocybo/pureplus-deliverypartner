package com.pureplus.delivery.data.remote

/**
 * Single source of truth for every backend path the DP app uses.
 *
 * The app targets the clean driver surface: /api/delivery/. The only remaining /api/admin
 * reference is the unused staff password login fallback (DP-01); the UI uses OTP instead.
 * Bulk/event fulfilment (DP-14) now lives on the clean driver surface (/api/delivery/bulk-jobs).
 * If the login fallback moves to /api/delivery later, this file is the only edit point.
 *
 * Verified against the delivery controllers on 2026-09-19; bulk repointed 2026-10-16.
 */
object ApiConstants {

    // DP-01 — driver login. Primary = OTP to the registered mobile (works for every onboarded driver;
    // no self-registration). Password login is a fallback that only works if the store admin set a
    // credential at onboarding (optional), so the app uses OTP.
    const val DELIVERY_SEND_OTP = "api/delivery/auth/send-otp"
    const val DELIVERY_VERIFY_OTP = "api/delivery/auth/verify-otp"
    const val LOGIN = "api/admin/auth/login" // optional password fallback (not used by the UI)

    // DP-02 / DP-19 / DP-22 — shift
    const val SHIFT_CHECK_IN        = "api/delivery/shifts/check-in"
    const val SHIFT_CHECK_IN_STATUS = "api/delivery/shifts/check-in/status"
    const val SHIFT_CHECK_OUT       = "api/delivery/shifts/check-out"
    const val SHIFT_ATTENDANCE      = "api/delivery/shifts/attendance"
    const val SHIFT_GEOFENCE        = "api/delivery/shifts/geofence" // DP-02 map: store centre + radius
    // ShiftV2 (Phase 2 DutyShift): end = ACTIVE→PENDING_RECON (reversible), resume = PENDING_RECON→ACTIVE.
    const val SHIFT_END             = "api/delivery/shifts/end"
    const val SHIFT_RESUME          = "api/delivery/shifts/resume"

    // DP-03 / DP-05 / DP-06 — the driver's own route
    const val ME_ROUTE   = "api/delivery/me/route"
    const val ME_STOPS   = "api/delivery/me/stops"
    const val ME_STOPS_COMPLETED = "api/delivery/me/stops/completed" // "Completed today" list
    fun meStop(orderId: Long) = "api/delivery/me/stops/$orderId"

    // DP-07 — background live-position ping
    const val ME_LOCATION = "api/delivery/me/location"

    // DP-17 / DP-18 — self goal + earnings
    const val ME_GOAL     = "api/delivery/me/goal"
    const val ME_EARNINGS = "api/delivery/me/earnings"

    // DP-20 / DP-21 — self-service (profile, documents, vehicle, store, shift, zone, delivery history)
    const val ME_PROFILE    = "api/delivery/me/profile"
    const val ME_DOCUMENTS  = "api/delivery/me/documents"
    const val ME_SHIFT      = "api/delivery/me/shift"
    const val ME_STORE      = "api/delivery/me/store"
    const val ME_VEHICLE    = "api/delivery/me/vehicle"
    const val ME_DELIVERIES = "api/delivery/me/deliveries"
    const val ME_ZONE       = "api/delivery/me/zone"

    // In-app notifications (driver inbox)
    const val ME_NOTIFICATIONS          = "api/delivery/me/notifications"
    const val ME_NOTIFICATIONS_UNREAD   = "api/delivery/me/notifications/unread-count"
    const val ME_NOTIFICATIONS_READ_ALL = "api/delivery/me/notifications/read-all"
    fun notificationRead(id: Long) = "api/delivery/me/notifications/$id/read"

    // DP-04 / DP-15 / DP-16 / DP-22 — reconciliation
    const val RECON_LOAD_OUT = "api/delivery/reconciliation/load-out"
    const val RECON_SESSIONS = "api/delivery/reconciliation/sessions"
    fun reconSession(id: Long)  = "api/delivery/reconciliation/sessions/$id"
    fun reconCanLines(id: Long) = "api/delivery/reconciliation/sessions/$id/can-lines"
    fun reconCash(id: Long)     = "api/delivery/reconciliation/sessions/$id/cash"
    fun reconClose(id: Long)    = "api/delivery/reconciliation/sessions/$id/close"
    // ShiftV2 (Phase 3): the driver's terminal write — OPEN→DRIVER_DECLARED, then awaits store verify.
    fun reconDeclare(id: Long)  = "api/delivery/reconciliation/sessions/$id/declare"

    // DP-08 / DP-10 / DP-11 / DP-12 — order actions (guarded: assigned-to-me + my store)
    fun orderDeliver(id: Long)        = "api/delivery/orders/$id/deliver"
    fun orderDeliverPreview(id: Long) = "api/delivery/orders/$id/deliver/preview" // DP-08 charge breakdown, no commit
    fun orderConfirmPayment(id: Long) = "api/delivery/orders/$id/confirm-payment"
    fun orderException(id: Long)      = "api/delivery/orders/$id/exceptions"
    fun orderAttempts(id: Long)       = "api/delivery/orders/$id/attempts" // DP-12 contact attempt (fail-gate)
    fun orderStatus(id: Long)         = "api/delivery/orders/$id/status"
    fun orderTransition(id: Long)     = "api/delivery/orders/$id/transition"
    fun orderDelivery(id: Long)       = "api/delivery/orders/$id/delivery"

    // DP-14 — bulk/event fulfilment on the clean driver surface.
    // GET api/delivery/bulk-jobs?status=  lists this driver's bulk jobs (list method added by the
    // data-layer agent); POST api/delivery/bulk-jobs/{id}/deliver fulfils one.
    const val BULK_JOBS = "api/delivery/bulk-jobs"
    fun bulkDeliver(id: Long) = "$BULK_JOBS/$id/deliver"

    // DP-03 / DP-04 / DP-15 — vehicle stock (cans on board / trips / returns).
    const val VEHICLE_STOCK         = "api/delivery/vehicle-stock"
    const val VEHICLE_STOCK_TRIPS   = "api/delivery/vehicle-stock/trips"
    const val VEHICLE_STOCK_RETURNS = "api/delivery/vehicle-stock/returns"
    const val VEHICLE_STOCK_RETURN  = "api/delivery/vehicle-stock/return" // POST undelivered fulls + empties
}
