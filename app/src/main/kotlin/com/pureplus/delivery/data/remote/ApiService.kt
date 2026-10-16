package com.pureplus.delivery.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit surface for the DP app. Fixed paths use their [ApiConstants] constant; id-bearing paths
 * are built by [ApiConstants] helpers and passed via [Url] (resolved against BuildConfig.BASE_URL).
 */
interface ApiService {

    // ── DP-01 Auth (bare responses, no envelope) ─────────────────────────────
    @POST(ApiConstants.DELIVERY_SEND_OTP)
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<SendOtpResponse>

    @POST(ApiConstants.DELIVERY_VERIFY_OTP)
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<VerifyOtpResponse>

    @POST(ApiConstants.LOGIN)
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ── DP-02 / DP-19 / DP-22 Shift ──────────────────────────────────────────
    @POST(ApiConstants.SHIFT_CHECK_IN)
    suspend fun checkIn(@Body request: CheckInRequest): Response<ApiResponse<CheckInDto>>

    @GET(ApiConstants.SHIFT_CHECK_IN_STATUS)
    suspend fun checkInStatus(@Query("shiftDate") shiftDate: String): Response<ApiResponse<CheckInStatus>>

    @POST(ApiConstants.SHIFT_CHECK_OUT)
    suspend fun checkOut(@Body request: CheckOutRequest): Response<ApiResponse<CheckInDto>>

    @GET(ApiConstants.SHIFT_ATTENDANCE)
    suspend fun attendance(
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiResponse<List<CheckInDto>>>

    @GET(ApiConstants.SHIFT_GEOFENCE)
    suspend fun geofence(): Response<ApiResponse<Geofence>>

    // ShiftV2 (Phase 2 DutyShift): end = ACTIVE→PENDING_RECON (reversible); resume = PENDING_RECON→ACTIVE.
    // Body is an optional { shiftDate } (reuses CheckOutRequest shape, lat/lng ignored); returns the shift row.
    @POST(ApiConstants.SHIFT_END)
    suspend fun endShift(@Body request: EndShiftRequest): Response<ApiResponse<CheckInDto>>

    @POST(ApiConstants.SHIFT_RESUME)
    suspend fun resumeShift(@Body request: EndShiftRequest): Response<ApiResponse<CheckInDto>>

    // ── DP-03 / DP-05 / DP-06 Route & stops ──────────────────────────────────
    @GET(ApiConstants.ME_ROUTE)
    suspend fun routeSummary(): Response<ApiResponse<RouteSummary>>

    @GET(ApiConstants.ME_STOPS)
    suspend fun stops(): Response<ApiResponse<List<Stop>>>

    @GET
    suspend fun stopDetail(@Url url: String): Response<ApiResponse<Stop>>

    // Orders completed (delivered) today — the "Completed today" list on the stops page.
    @GET(ApiConstants.ME_STOPS_COMPLETED)
    suspend fun stopsCompleted(): Response<ApiResponse<List<Stop>>>

    // ── DP-07 live position ──────────────────────────────────────────────────
    @POST(ApiConstants.ME_LOCATION)
    suspend fun ping(@Body ping: LocationPing): Response<ApiResponse<Unit>>

    // ── DP-17 / DP-18 goal & earnings ────────────────────────────────────────
    @GET(ApiConstants.ME_GOAL)
    suspend fun goal(@Query("date") date: String?): Response<ApiResponse<GoalResponse>>

    @GET(ApiConstants.ME_EARNINGS)
    suspend fun earnings(
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiResponse<EarningsResponse>>

    // ── DP-04 / DP-15 / DP-16 / DP-22 reconciliation ─────────────────────────
    @POST(ApiConstants.RECON_LOAD_OUT)
    suspend fun loadOut(@Body request: RecordLoadOutRequest): Response<ApiResponse<ReconciliationSession>>

    @POST(ApiConstants.RECON_SESSIONS)
    suspend fun openSession(@Body request: OpenSessionRequest): Response<ApiResponse<ReconciliationSession>>

    @GET
    suspend fun getSession(@Url url: String): Response<ApiResponse<ReconciliationSession>>

    @PUT
    suspend fun setCanLine(@Url url: String, @Body request: SetCanLineRequest): Response<ApiResponse<ReconciliationSession>>

    @PUT
    suspend fun setCash(@Url url: String, @Body request: SetCashRequest): Response<ApiResponse<ReconciliationSession>>

    @POST
    suspend fun closeSession(@Url url: String, @Body request: CloseSessionRequest): Response<ApiResponse<ReconciliationSession>>

    // ShiftV2 (Phase 3): the driver's terminal declaration — OPEN→DRIVER_DECLARED (no body).
    @POST
    suspend fun declareSession(@Url url: String): Response<ApiResponse<ReconciliationSession>>

    // ── DP-08 / DP-10 / DP-11 / DP-12 order actions ──────────────────────────
    @POST
    suspend fun deliver(@Url url: String, @Body request: RecordDeliveryRequest): Response<ApiResponse<DeliveryExecutionResult>>

    // DP-08 preview: the charge breakdown (floor + deposit + payable) for the given floor/empties, no commit.
    // Same result shape as /deliver.
    @POST
    suspend fun deliverPreview(@Url url: String, @Body request: RecordDeliveryRequest): Response<ApiResponse<DeliveryExecutionResult>>

    @POST
    suspend fun confirmPayment(@Url url: String): Response<ApiResponse<DeliveryExecutionResult>>

    // DP-12 contact attempt (fail-gate). Returns the new attempt id as a bare integer in the envelope.
    @POST
    suspend fun recordAttempt(@Url url: String, @Body request: RecordAttemptRequest): Response<ApiResponse<Int>>

    @POST
    suspend fun raiseException(@Url url: String, @Body request: RaiseExceptionRequest): Response<ApiResponse<OrderExceptionDto>>

    @POST
    suspend fun transition(@Url url: String, @Body request: TransitionRequest): Response<ApiResponse<Map<String, Any>>>

    @GET
    suspend fun orderStatus(@Url url: String): Response<ApiResponse<OrderStatus>>

    // ── DP-14 bulk fulfilment ────────────────────────────────────────────────
    // List this driver's bulk jobs (default status OUT_FOR_DELIVERY).
    @GET(ApiConstants.BULK_JOBS)
    suspend fun bulkJobs(@Query("status") status: String?): Response<ApiResponse<List<BulkJob>>>

    @POST
    suspend fun bulkDeliver(@Url url: String): Response<ApiResponse<BulkDeliveryResult>>

    // ── DP-21 self-service ────────────────────────────────────────────────────
    @GET(ApiConstants.ME_PROFILE)
    suspend fun profile(): Response<ApiResponse<DriverProfile>>

    @PUT(ApiConstants.ME_PROFILE)
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiResponse<DriverProfile>>

    @GET(ApiConstants.ME_DOCUMENTS)
    suspend fun documents(): Response<ApiResponse<List<DriverDocument>>>

    // Submit a document (lands PENDING for Store-Admin approval) → returns the driver's FULL document list.
    @POST(ApiConstants.ME_DOCUMENTS)
    suspend fun submitDocument(@Body request: SubmitDocumentRequest): Response<ApiResponse<List<DriverDocument>>>

    @GET(ApiConstants.ME_SHIFT)
    suspend fun shiftInfo(): Response<ApiResponse<DriverShiftInfo>>

    @GET(ApiConstants.ME_STORE)
    suspend fun store(): Response<ApiResponse<DriverStore>>

    @GET(ApiConstants.ME_VEHICLE)
    suspend fun vehicle(): Response<ApiResponse<DriverVehicle>>

    @GET(ApiConstants.ME_ZONE)
    suspend fun zones(): Response<ApiResponse<List<DriverZone>>>

    // ── DP-20 delivery history ─────────────────────────────────────────────────
    @GET(ApiConstants.ME_DELIVERIES)
    suspend fun deliveries(
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiResponse<List<DriverDelivery>>>

    // ── In-app notifications ───────────────────────────────────────────────────
    @GET(ApiConstants.ME_NOTIFICATIONS)
    suspend fun notifications(
        @Query("unreadOnly") unreadOnly: Boolean,
        @Query("limit") limit: Int,
    ): Response<ApiResponse<List<NotificationItem>>>

    @GET(ApiConstants.ME_NOTIFICATIONS_UNREAD)
    suspend fun unreadCount(): Response<ApiResponse<UnreadCount>>

    @POST
    suspend fun markNotificationRead(@Url url: String): Response<ApiResponse<MarkedResult>>

    @POST(ApiConstants.ME_NOTIFICATIONS_READ_ALL)
    suspend fun markAllNotificationsRead(): Response<ApiResponse<MarkedCountResult>>

    // ── DP-03 / DP-04 / DP-15 vehicle stock ──────────────────────────────────────
    @GET(ApiConstants.VEHICLE_STOCK)
    suspend fun vehicleStock(): Response<ApiResponse<VehicleStock>>

    @GET(ApiConstants.VEHICLE_STOCK_TRIPS)
    suspend fun vehicleTrips(
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiResponse<List<VehicleTrip>>>

    @GET(ApiConstants.VEHICLE_STOCK_RETURNS)
    suspend fun vehicleReturns(
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiResponse<List<VehicleReturn>>>

    // DP-15 return: undelivered fulls + collected empties back to store → the refreshed VehicleStock.
    @POST(ApiConstants.VEHICLE_STOCK_RETURN)
    suspend fun vehicleReturn(@Body request: VehicleReturnRequest): Response<ApiResponse<VehicleStock>>
}
