package com.pureplus.delivery.data.repository

import com.pureplus.delivery.data.remote.ApiConstants
import com.pureplus.delivery.data.remote.CheckInDto
import com.pureplus.delivery.data.remote.CheckInRequest
import com.pureplus.delivery.data.remote.CheckInStatus
import com.pureplus.delivery.data.remote.CheckOutRequest
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
import com.pureplus.delivery.data.remote.UnreadCount
import com.pureplus.delivery.data.remote.UpdateProfileRequest
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
    suspend fun shiftInfo(): ApiResult<DriverShiftInfo> = apiCall { api.shiftInfo() }
    suspend fun store(): ApiResult<DriverStore> = apiCall { api.store() }
    suspend fun vehicle(): ApiResult<DriverVehicle> = apiCall { api.vehicle() }
    suspend fun zones(): ApiResult<List<DriverZone>> = apiCall { api.zones() }

    // DP-20 delivery history
    suspend fun deliveries(from: String? = null, to: String? = null): ApiResult<List<DriverDelivery>> =
        apiCall { api.deliveries(from, to) }

    // In-app notifications
    suspend fun notifications(unreadOnly: Boolean = false, limit: Int = 50): ApiResult<List<NotificationItem>> =
        apiCall { api.notifications(unreadOnly, limit) }
    suspend fun unreadCount(): ApiResult<UnreadCount> = apiCall { api.unreadCount() }
    suspend fun markNotificationRead(id: Long): ApiResult<MarkedResult> =
        apiCall { api.markNotificationRead(ApiConstants.notificationRead(id)) }
    suspend fun markAllNotificationsRead(): ApiResult<MarkedCountResult> =
        apiCall { api.markAllNotificationsRead() }
}
