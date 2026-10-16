package com.pureplus.delivery.data.repository

import com.pureplus.delivery.data.remote.ApiConstants
import com.pureplus.delivery.data.remote.CheckInDto
import com.pureplus.delivery.data.remote.CheckInRequest
import com.pureplus.delivery.data.remote.CheckInStatus
import com.pureplus.delivery.data.remote.CheckOutRequest
import com.pureplus.delivery.data.remote.EndShiftRequest
import com.pureplus.delivery.data.remote.DriverDelivery
import com.pureplus.delivery.data.remote.DriverDocument
import com.pureplus.delivery.data.remote.DriverProfile
import com.pureplus.delivery.data.remote.DriverShiftInfo
import com.pureplus.delivery.data.remote.DriverStore
import com.pureplus.delivery.data.remote.DriverVehicle
import com.pureplus.delivery.data.remote.DriverZone
import com.pureplus.delivery.data.remote.EarningsResponse
import com.pureplus.delivery.data.remote.Geofence
import com.pureplus.delivery.data.remote.GoalResponse
import com.pureplus.delivery.data.remote.LocationPing
import com.pureplus.delivery.data.remote.MarkedCountResult
import com.pureplus.delivery.data.remote.MarkedResult
import com.pureplus.delivery.data.remote.NotificationItem
import com.pureplus.delivery.data.remote.RetrofitClient
import com.pureplus.delivery.data.remote.RouteSummary
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.data.remote.SubmitDocumentRequest
import com.pureplus.delivery.data.remote.UnreadCount
import com.pureplus.delivery.data.remote.UpdateProfileRequest
import com.pureplus.delivery.data.remote.VehicleReturn
import com.pureplus.delivery.data.remote.VehicleReturnRequest
import com.pureplus.delivery.data.remote.VehicleStock
import com.pureplus.delivery.data.remote.VehicleTrip
import com.pureplus.delivery.util.todayIso

/**
 * The driver's own reads + shift lifecycle (DP-02/03/05/06/07/17/18/19/22).
 * Driver id + store come from the JWT server-side, so nothing identity-related is sent here.
 */
class DriverRepository {
    private val api = RetrofitClient.apiService

    // DP-03 route home
    suspend fun routeSummary(): ApiResult<RouteSummary> = apiCall { api.routeSummary() }

    // DP-05 stop list
    suspend fun stops(): ApiResult<List<Stop>> = apiCall { api.stops() }

    // DP-06 stop detail (items included)
    suspend fun stopDetail(orderId: Long): ApiResult<Stop> = apiCall { api.stopDetail(ApiConstants.meStop(orderId)) }

    // "Completed today" list (orders this driver has already delivered today).
    suspend fun stopsCompleted(): ApiResult<List<Stop>> = apiCall { api.stopsCompleted() }

    // DP-02 check-in (geo-fenced). lat/long null = GPS failure → needs an override.
    suspend fun checkIn(
        lat: Double?,
        lng: Double?,
        override: Boolean = false,
        overrideReason: String? = null,
        shiftDate: String = todayIso(),
    ): ApiResult<CheckInDto> = apiCall {
        api.checkIn(CheckInRequest(shiftDate, lat, lng, override, overrideReason))
    }

    suspend fun checkInStatus(shiftDate: String = todayIso()): ApiResult<CheckInStatus> =
        apiCall { api.checkInStatus(shiftDate) }

    // DP-02 geofence (store centre + radius) for the check-in map.
    suspend fun geofence(): ApiResult<Geofence> = apiCall { api.geofence() }

    // DP-22 check-out (attendance close; the reconciliation close is the real gate).
    suspend fun checkOut(lat: Double?, lng: Double?, shiftDate: String = todayIso()): ApiResult<CheckInDto> =
        apiCall { api.checkOut(CheckOutRequest(shiftDate, lat, lng)) }

    // ShiftV2 (Phase 2): end the shift → PENDING_RECON (blocked while active orders remain). Reversible.
    suspend fun endShift(shiftDate: String = todayIso()): ApiResult<CheckInDto> =
        apiCall { api.endShift(EndShiftRequest(shiftDate)) }

    // ShiftV2 (Phase 2): resume a shift ended by mistake → ACTIVE.
    suspend fun resumeShift(shiftDate: String = todayIso()): ApiResult<CheckInDto> =
        apiCall { api.resumeShift(EndShiftRequest(shiftDate)) }

    // DP-19 attendance history
    suspend fun attendance(from: String? = null, to: String? = null): ApiResult<List<CheckInDto>> =
        apiCall { api.attendance(from, to) }

    // DP-17 earnings
    suspend fun earnings(from: String? = null, to: String? = null): ApiResult<EarningsResponse> =
        apiCall { api.earnings(from, to) }

    // DP-18 daily goal
    suspend fun goal(date: String? = todayIso()): ApiResult<GoalResponse> = apiCall { api.goal(date) }

    // DP-07 background live-position ping (fire-and-forget; failures are swallowed).
    suspend fun ping(lat: Double, lng: Double, accuracy: Double): ApiResult<Unit> =
        apiCall { api.ping(LocationPing(lat, lng, accuracy)) }

    // DP-21 self-service
    suspend fun profile(): ApiResult<DriverProfile> = apiCall { api.profile() }
    suspend fun updateContactNumber(contactNumber: String): ApiResult<DriverProfile> =
        apiCall { api.updateProfile(UpdateProfileRequest(contactNumber)) }
    suspend fun documents(): ApiResult<List<DriverDocument>> = apiCall { api.documents() }
    // Submit a document for Store-Admin approval → returns the refreshed full document list.
    suspend fun submitDocument(request: SubmitDocumentRequest): ApiResult<List<DriverDocument>> =
        apiCall { api.submitDocument(request) }
    suspend fun shiftInfo(): ApiResult<DriverShiftInfo> = apiCall { api.shiftInfo() }
    suspend fun store(): ApiResult<DriverStore> = apiCall { api.store() }
    suspend fun vehicle(): ApiResult<DriverVehicle> = apiCall { api.vehicle() }
    suspend fun zones(): ApiResult<List<DriverZone>> = apiCall { api.zones() }

    // DP-20 delivery history
    suspend fun deliveries(from: String? = null, to: String? = null): ApiResult<List<DriverDelivery>> =
        apiCall { api.deliveries(from, to) }

    // DP-03 / DP-04 / DP-15 vehicle stock (cans on board, trips, returns).
    suspend fun vehicleStock(): ApiResult<VehicleStock> = apiCall { api.vehicleStock() }
    suspend fun vehicleTrips(from: String? = null, to: String? = null): ApiResult<List<VehicleTrip>> =
        apiCall { api.vehicleTrips(from, to) }
    suspend fun vehicleReturns(from: String? = null, to: String? = null): ApiResult<List<VehicleReturn>> =
        apiCall { api.vehicleReturns(from, to) }
    // DP-15 record a return (undelivered fulls + collected empties) → the refreshed stock snapshot.
    suspend fun recordReturn(request: VehicleReturnRequest): ApiResult<VehicleStock> =
        apiCall { api.vehicleReturn(request) }

    // In-app notifications
    suspend fun notifications(unreadOnly: Boolean = false, limit: Int = 50): ApiResult<List<NotificationItem>> =
        apiCall { api.notifications(unreadOnly, limit) }
    suspend fun unreadCount(): ApiResult<UnreadCount> = apiCall { api.unreadCount() }
    suspend fun markNotificationRead(id: Long): ApiResult<MarkedResult> =
        apiCall { api.markNotificationRead(ApiConstants.notificationRead(id)) }
    suspend fun markAllNotificationsRead(): ApiResult<MarkedCountResult> =
        apiCall { api.markAllNotificationsRead() }
}
