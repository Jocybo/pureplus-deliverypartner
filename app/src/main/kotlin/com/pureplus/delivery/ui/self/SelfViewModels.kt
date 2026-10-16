package com.pureplus.delivery.ui.self

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.data.remote.CheckInDto
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
import com.pureplus.delivery.data.remote.NotificationItem
import com.pureplus.delivery.data.remote.SubmitDocumentRequest
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

// ── DP-17 Earnings ────────────────────────────────────────────────────────────
data class EarningsState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val earnings: EarningsResponse? = null,
    val error: String? = null,
)

class EarningsViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(EarningsState())
    val state = _state.asStateFlow()

    init { load() }

    /** First load — full-screen loader. */
    fun load() = fetch(initial = true)

    /** Pull-to-refresh — pull indicator, not the full-screen loader. */
    fun refresh() = fetch(initial = false)

    private fun fetch(initial: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            val from = LocalDate.now().withDayOfMonth(1).toString()
            val to = LocalDate.now().toString()
            when (val r = driver.earnings(from, to)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, refreshing = false, earnings = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, refreshing = false, error = r.message) }
            }
        }
    }

    companion object { val Factory = viewModelFactory { initializer { EarningsViewModel(Deps.driverRepository) } } }
}

// ── DP-18 Daily goal ──────────────────────────────────────────────────────────
data class DailyGoalState(
    val loading: Boolean = true,
    val goal: GoalResponse? = null,
    val error: String? = null,
)

class DailyGoalViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(DailyGoalState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = driver.goal()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, goal = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    companion object { val Factory = viewModelFactory { initializer { DailyGoalViewModel(Deps.driverRepository) } } }
}

// ── DP-19 Attendance history ──────────────────────────────────────────────────
data class AttendanceState(
    val loading: Boolean = true,
    val days: List<CheckInDto> = emptyList(),
    val error: String? = null,
)

class AttendanceViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(AttendanceState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val from = LocalDate.now().minusDays(30).toString()
            val to = LocalDate.now().toString()
            when (val r = driver.attendance(from, to)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, days = r.data.sortedByDescending { d -> d.shiftDate }) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    companion object { val Factory = viewModelFactory { initializer { AttendanceViewModel(Deps.driverRepository) } } }
}

// ── DP-19 Attendance detail ───────────────────────────────────────────────────
// One check-in record + the store geofence (centre + config radius) so the map can draw the fence and
// mark where the driver actually checked in. There's no single-record endpoint, so we re-pull the
// attendance list and find this id (the record is always within the range the list already showed).
data class AttendanceDetailState(
    val loading: Boolean = true,
    val record: CheckInDto? = null,
    val geofence: Geofence? = null,
    val error: String? = null,
)

class AttendanceDetailViewModel(
    private val driver: DriverRepository,
    private val checkInId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(AttendanceDetailState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val attendance = driver.attendance()
            val geo = driver.geofence()   // store centre + config radius for the fence circle
            when (attendance) {
                is ApiResult.Success -> {
                    val record = attendance.data.firstOrNull { it.id.toLong() == checkInId }
                    if (record == null) {
                        _state.update { it.copy(loading = false, error = "Attendance record not found.") }
                    } else {
                        _state.update {
                            it.copy(loading = false, record = record, geofence = (geo as? ApiResult.Success)?.data)
                        }
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = attendance.message) }
            }
        }
    }

    companion object {
        fun factory(checkInId: Long) = viewModelFactory {
            initializer { AttendanceDetailViewModel(Deps.driverRepository, checkInId) }
        }
    }
}

// ── DP-20 Delivery history ────────────────────────────────────────────────────
// Real driver delivery history via GET /me/deliveries (defaults to the last 30 days on the backend).
data class HistoryState(
    val loading: Boolean = true,
    val deliveries: List<DriverDelivery> = emptyList(),
    val error: String? = null,
)

class HistoryViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val from = LocalDate.now().minusDays(30).toString()
            val to = LocalDate.now().toString()
            when (val r = driver.deliveries(from, to)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, deliveries = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    companion object { val Factory = viewModelFactory { initializer { HistoryViewModel(Deps.driverRepository) } } }
}

// ── In-app notifications (driver inbox) ─────────────────────────────────────────
data class NotificationsState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val items: List<NotificationItem> = emptyList(),
    val error: String? = null,
) {
    val unreadCount: Int get() = items.count { !it.isRead }
}

class NotificationsViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() = fetch(initial = true)
    fun refresh() = fetch(initial = false)

    private fun fetch(initial: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            when (val r = driver.notifications()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, refreshing = false, items = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, refreshing = false, error = r.message) }
            }
        }
    }

    fun markRead(id: Long) {
        if (_state.value.items.firstOrNull { it.id == id }?.isRead == true) return
        // Optimistic — flip locally, then persist (best-effort).
        _state.update { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(isRead = true) else it }) }
        viewModelScope.launch { driver.markNotificationRead(id) }
    }

    fun markAllRead() {
        if (_state.value.items.none { !it.isRead }) return
        _state.update { s -> s.copy(items = s.items.map { it.copy(isRead = true) }) }
        viewModelScope.launch { driver.markAllNotificationsRead() }
    }

    companion object {
        val Factory = viewModelFactory { initializer { NotificationsViewModel(Deps.driverRepository) } }
    }
}

// ── DP-21 Profile ─────────────────────────────────────────────────────────────
data class ProfileState(
    val loading: Boolean = true,
    // token-sourced header values, shown immediately while the network calls resolve
    val name: String = "",
    val employeeId: Long = -1,
    val storeId: Int = -1,
    // network-sourced detail
    val profile: DriverProfile? = null,
    val vehicle: DriverVehicle? = null,
    val store: DriverStore? = null,
    val shift: DriverShiftInfo? = null,
    val zones: List<DriverZone> = emptyList(),
    val documents: List<DriverDocument> = emptyList(),
    val unread: Int = 0,
    val savingContact: Boolean = false,
    val submittingDoc: Boolean = false,
    val docMessage: String? = null,
    val error: String? = null,
)

class ProfileViewModel(private val driver: DriverRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    name = Deps.tokenStore.getFullName(),
                    employeeId = Deps.tokenStore.getEmployeeId(),
                    storeId = Deps.tokenStore.getStoreId(),
                )
            }
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // Each read is best-effort — a missing vehicle/zone shouldn't blank the whole screen.
            val profile = driver.profile()
            val vehicle = driver.vehicle()
            val store = driver.store()
            val shift = driver.shiftInfo()
            val zones = driver.zones()
            val documents = driver.documents()
            val unread = driver.unreadCount()
            _state.update {
                it.copy(
                    loading = false,
                    profile = (profile as? ApiResult.Success)?.data ?: it.profile,
                    vehicle = (vehicle as? ApiResult.Success)?.data ?: it.vehicle,
                    store = (store as? ApiResult.Success)?.data ?: it.store,
                    shift = (shift as? ApiResult.Success)?.data ?: it.shift,
                    zones = (zones as? ApiResult.Success)?.data ?: it.zones,
                    documents = (documents as? ApiResult.Success)?.data ?: it.documents,
                    unread = (unread as? ApiResult.Success)?.data?.unread ?: it.unread,
                    error = (profile as? ApiResult.Failure)?.message,
                )
            }
        }
    }

    /**
     * Submit a document for Store-Admin approval (DP-21). No file-upload vendor is wired, so we send the
     * fields the backend expects (type + number + optional file URL / dates); it lands as PENDING and the
     * backend returns the refreshed full document list. [fileUrl] is optional (a link the driver pastes).
     */
    fun submitDocument(
        docType: String,
        docNumber: String?,
        fileUrl: String?,
        issueDate: String?,
        expiryDate: String?,
        onDone: (Boolean) -> Unit = {},
    ) {
        if (_state.value.submittingDoc) return
        viewModelScope.launch {
            _state.update { it.copy(submittingDoc = true, docMessage = null, error = null) }
            val req = SubmitDocumentRequest(
                docType = docType,
                docNumber = docNumber?.ifBlank { null },
                fileUrl = fileUrl?.ifBlank { null },
                issueDate = issueDate?.ifBlank { null },
                expiryDate = expiryDate?.ifBlank { null },
            )
            when (val r = driver.submitDocument(req)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submittingDoc = false, documents = r.data, docMessage = "Submitted — pending store approval.") }
                    onDone(true)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(submittingDoc = false, error = r.message) }
                    onDone(false)
                }
            }
        }
    }

    /** The one field a driver may self-edit. */
    fun updateContactNumber(number: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(savingContact = true, error = null) }
            when (val r = driver.updateContactNumber(number.trim())) {
                is ApiResult.Success -> {
                    _state.update { it.copy(savingContact = false, profile = r.data) }
                    onDone(true)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(savingContact = false, error = r.message) }
                    onDone(false)
                }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            Deps.authRepository.logout()
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { ProfileViewModel(Deps.driverRepository) } }
    }
}
