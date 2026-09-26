package com.pureplus.delivery.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Wire models for the clean /api/delivery surface. Field names mirror the backend DTOs exactly
 * (camelCase on the wire; Gson maps Kotlin property names 1:1). Verified against the controllers +
 * DTOs on 2026-09-19. See projectdocuments/handoff/Delivery-API.md + Delivery-Payloads.md.
 */

// ── Generic envelope ─────────────────────────────────────────────────────────
// Every /api/delivery endpoint returns { success, data, message? }. (Login is the one exception —
// it returns a bare LoginResponse, no envelope.)
data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
)

// ── DP-01 Auth ────────────────────────────────────────────────────────────────
// Primary: OTP (POST /api/delivery/auth/send-otp → /verify-otp). Both return BARE bodies (not the
// {success,data} envelope). send-otp returns the demo OTP while SMS is vendor-gated.
data class SendOtpRequest(val mobileNumber: String)
data class VerifyOtpRequest(val mobileNumber: String, val otp: String)

data class SendOtpResponse(
    val otp: String? = null,          // demo OTP echoed back until MSG91 is wired
    val message: String? = null,
    val expiresAt: String? = null,
    val retryAfterSeconds: Int? = null, // present on 429 rate-limit
)

data class VerifyOtpResponse(
    val success: Boolean = false,
    val token: String? = null,
    val employeeId: Int = 0,
    val name: String? = null,
    val roleId: Int = 0,
    val storeId: Int? = null,
    val message: String? = null,      // present on failure
)

// Optional password fallback (POST /api/admin/auth/login — bare body). Not used by the UI.
data class LoginRequest(val username: String, val password: String)

data class LoginResponse(
    val token: String? = null,
    val roleId: Int = 0,
    val storeId: Int? = null,
    val employeeId: Int = 0,
    val name: String? = null,
    val message: String? = null,
)

// ── DP-02 / DP-22 Shift ───────────────────────────────────────────────────────
data class CheckInRequest(
    val shiftDate: String,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName("override") val isOverride: Boolean = false,
    val overrideReason: String? = null,
)

data class CheckOutRequest(
    val shiftDate: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class CheckInDto(
    val id: Int = 0,
    val driverId: Int = 0,
    val storeId: Int = 0,
    val shiftDate: String = "",
    val checkedInAt: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceMeters: Double? = null,
    val radiusMeters: Int = 0,
    val withinFence: Boolean = false,
    val isOverride: Boolean = false,
    val overrideReason: String? = null,
    val status: String = "",
    val checkedOutAt: String? = null,
)

data class CheckInStatus(
    val checkedIn: Boolean = false,
    val checkIn: CheckInDto? = null,
)

// DP-02 geofence for the check-in map — store centre + radius. A null latitude/longitude ⇒ the store
// has no location set (nothing to draw against).
data class Geofence(
    val storeId: Int = 0,
    val storeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int = 0,
)

// ── DP-07 live position ping ───────────────────────────────────────────────────
data class LocationPing(
    val latitude: Double,
    val longitude: Double,
    val accuracyRate: Double,
)

// ── DP-03 route home ───────────────────────────────────────────────────────────
data class RouteSummary(
    val stopsPending: Int = 0,
    val stopsCompletedToday: Int = 0,
    val cansToLoad: Int = 0,
)

// ── DP-05 / DP-06 stops ─────────────────────────────────────────────────────────
data class Stop(
    val orderId: Int = 0,
    val status: String? = null,
    val expectedDeliveryTime: String? = null,
    val paymentTypeId: Int = 0,
    val paymentMode: String = "",
    val totalAmount: Double = 0.0,
    val cans: Int = 0,
    val receiverName: String? = null,
    val receiverNumber: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val pincode: String? = null,
    val landmark: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val floorNo: Int? = null,
    val lift: Boolean? = null,
    /** present on the DP-06 detail response only. */
    val items: List<StopItem>? = null,
)

data class StopItem(
    val productId: Int = 0,
    val productName: String? = null,
    val quantity: Int = 0,
    val orderedQuantity: Int = 0,
    val unitPrice: Double = 0.0,
)

// ── DP-04 / DP-15 / DP-16 / DP-22 reconciliation ─────────────────────────────────
data class LoadOutLine(val productId: Int, val quantity: Int)

data class RecordLoadOutRequest(
    val shiftDate: String,
    val lines: List<LoadOutLine>,
    // driverId/storeId are forced from the JWT server-side; sent as 0 and ignored.
    val driverId: Int = 0,
    val storeId: Int = 0,
)

data class OpenSessionRequest(
    val shiftDate: String,
    val driverId: Int = 0,
    val storeId: Int = 0,
)

data class SetCanLineRequest(
    val productId: Int,
    val loadedOut: Int,
    val delivered: Int,
    val fullsReturned: Int,
    val emptiesCollected: Int,
    val breakageLoss: Int,
)

data class SetCashRequest(
    val expectedCash: Double,
    val expectedUpi: Double,
    val expectedPostpaid: Double,
    val expectedPending: Double,
    val declaredCash: Double,
    val tolerance: Double,
)

data class CloseSessionRequest(val closeNote: String? = null)

data class CanLine(
    val productId: Int = 0,
    val loadedOut: Int = 0,
    val delivered: Int = 0,
    val fullsReturned: Int = 0,
    val emptiesCollected: Int = 0,
    val breakageLoss: Int = 0,
    val variance: Int = 0,
    val balanced: Boolean = false,
)

data class CashSummary(
    val expectedCash: Double = 0.0,
    val expectedUpi: Double = 0.0,
    val expectedPostpaid: Double = 0.0,
    val expectedPending: Double = 0.0,
    val declaredCash: Double = 0.0,
    val tolerance: Double = 0.0,
    val cashVariance: Double = 0.0,
    val cashReconciled: Boolean = false,
)

data class ReconciliationSession(
    val id: Int = 0,
    val driverId: Int = 0,
    val storeId: Int = 0,
    val shiftDate: String = "",
    val status: String = "",
    val loadOutAt: String? = null,
    val closedAt: String? = null,
    val closeNote: String? = null,
    val lines: List<CanLine> = emptyList(),
    val allBalanced: Boolean = false,
    val cash: CashSummary? = null,
    val readyToClose: Boolean = false,
)

// ── DP-08 / DP-10 delivery execution ─────────────────────────────────────────────
data class DeliveryLine(
    val productId: Int,
    val deliveredQty: Int,
    val emptiesCollected: Int,
    val unitPrice: Double,
)

data class RecordDeliveryRequest(
    val actualFloor: Int?,
    val actualHasLift: Boolean?,
    val lines: List<DeliveryLine>,
    /** CASH | UPI | POSTPAID | WALLET. */
    val paymentMode: String,
    /** DP-10 offline: record the doorstep now, defer settlement → DELIVERED_PAYMENT_PENDING. */
    val paymentPending: Boolean = false,
    val notes: String? = null,
)

data class DeliveryLineResult(
    val productId: Int = 0,
    val deliveredQty: Int = 0,
    val emptiesCollected: Int = 0,
    val isReusable: Boolean = false,
    val unreturnedCans: Int = 0,
    val depositPerCan: Double = 0.0,
    val lineDeposit: Double = 0.0,
)

data class DeliveryExecutionResult(
    val orderId: Int = 0,
    val customerId: Int = 0,
    val alreadyRecorded: Boolean = false,
    val paymentPending: Boolean = false,
    val baselineFloor: Int? = null,
    val baselineHasLift: Boolean? = null,
    val actualFloor: Int = 0,
    val actualHasLift: Boolean = false,
    val baselineFloorCharge: Double = 0.0,
    val actualFloorCharge: Double = 0.0,
    val floorDelta: Double = 0.0,
    val walletCredit: Double = 0.0,
    val lines: List<DeliveryLineResult> = emptyList(),
    val extraDeposit: Double = 0.0,
    val orderAmount: Double = 0.0,
    val amountPayable: Double = 0.0,
    val paymentMode: String = "",
    val lifecycleResult: String = "",
    val summary: String = "",
)

// ── DP-12 exceptions ─────────────────────────────────────────────────────────────
data class RaiseExceptionRequest(
    val type: String,
    val reason: String?,
    val notes: String?,
)

data class OrderExceptionDto(
    val id: Int = 0,
    val orderId: Int = 0,
    val storeId: Int? = null,
    val type: String = "",
    val reason: String? = null,
    val notes: String? = null,
    val status: String = "",
    val raisedBy: Int? = null,
    val resolvedBy: Int? = null,
    val resolutionNotes: String? = null,
    val createdAt: String = "",
    val resolvedAt: String? = null,
)

// ── lifecycle transition (DP-11 continue / status walk) ───────────────────────────
data class TransitionRequest(val toStatus: String, val remarks: String? = null)

data class OrderStatus(
    val orderId: Int = 0,
    val currentStatus: String = "",
    val allowedTransitions: List<String> = emptyList(),
    val customerCancellable: Boolean = false,
)

// ── DP-17 / DP-18 goal & earnings ────────────────────────────────────────────────
data class GoalResponse(
    val employeeId: Int = 0,
    val date: String = "",
    val completedDeliveries: Int = 0,
    val dailyGoalTarget: Int = 0,
    val achievementPercent: Double = 0.0,
    val goalMet: Boolean = false,
)

data class EarningsResponse(
    val employeeId: Int = 0,
    val employeeName: String? = null,
    val fromDate: String = "",
    val toDate: String = "",
    val periodDays: Int = 0,
    val completedDeliveries: Int = 0,
    val periodGoalTarget: Int = 0,
    val achievementPercent: Double = 0.0,
    val baseSalary: Double = 0.0,
    val perDeliveryBonusTotal: Double = 0.0,
    val goalComponent: Double = 0.0,
    val totalEarnings: Double = 0.0,
)

// ── DP-21 self-service (profile / documents / vehicle / store / shift / zone) ─────
data class DriverProfile(
    val id: Int = 0,
    val name: String? = null,
    val contactNumber: String? = null,
    val roleName: String? = null,
    val storeId: Int? = null,
    val storeName: String? = null,
    val joiningDate: String? = null,
    val qualification: String? = null,
    val experienceYears: Int? = null,
    val status: String? = null,
)

/** The one field a driver may self-edit (PUT /me/profile). */
data class UpdateProfileRequest(val contactNumber: String?)

data class DriverDocument(
    val id: Int = 0,
    val docType: String? = null,
    val docNumber: String? = null,
    val fileUrl: String? = null,
    val issueDate: String? = null,
    val expiryDate: String? = null,
    val status: String? = null,
)

data class DriverShiftInfo(
    val shiftId: Int? = null,
    val shiftName: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val days: List<Int> = emptyList(),   // 0=Sun .. 6=Sat
)

data class DriverStore(
    val storeId: Int = 0,
    val storeName: String? = null,
    val area: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geofenceRadiusMeters: Int? = null,
)

data class DriverVehicle(
    val vehicleId: Int = 0,
    val vehicleNumber: String? = null,
    val vehicleType: String? = null,
    val capacity: Int? = null,
    val insuranceProvider: String? = null,
    val insuranceValidTo: String? = null,
    val nextServiceDue: String? = null,
)

// ── DP-20 delivery history ────────────────────────────────────────────────────────
data class DriverDelivery(
    val orderId: Int = 0,
    val status: String? = null,
    val deliveredAt: String? = null,
    val amount: Double? = null,
    val address: String? = null,
)

data class DriverZone(
    val zoneId: Int = 0,
    val zoneName: String? = null,
    val pincodes: List<String> = emptyList(),
)

// ── In-app notifications (driver inbox) ─────────────────────────────────────────────
data class NotificationItem(
    val id: Long = 0,
    val category: String? = null,
    val title: String? = null,
    val body: String = "",
    val link: String? = null,
    val isRead: Boolean = false,
    val createdAt: String = "",
    val readAt: String? = null,
)

data class UnreadCount(val unread: Int = 0)

/** POST .../{id}/read → { marked: Boolean }. */
data class MarkedResult(val marked: Boolean = false)

/** POST .../read-all → { marked: Int } (count marked). */
data class MarkedCountResult(val marked: Int = 0)
