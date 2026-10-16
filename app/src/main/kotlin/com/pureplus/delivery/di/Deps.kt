package com.pureplus.delivery.di

import com.pureplus.delivery.PurePlusDeliveryApp
import com.pureplus.delivery.data.local.TokenStore
import com.pureplus.delivery.data.repository.AuthRepository
import com.pureplus.delivery.data.repository.DeliveryRepository
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.data.repository.OutboxRepository
import com.pureplus.delivery.data.repository.ReconciliationRepository
import com.pureplus.delivery.domain.DeliverPreview
import com.pureplus.delivery.domain.DeliveryFailGate
import com.pureplus.delivery.domain.ReconciliationFlow
import com.pureplus.delivery.domain.ShiftLifecycle

/**
 * Hand-rolled service locator. This app deliberately has no DI framework (Hilt/Koin) — the
 * dependency graph is tiny, so a lazy singleton holder keeps it obvious and fast to sync.
 * ViewModels read their repositories (and domain use-cases) from here (see each *ViewModel.Factory).
 */
object Deps {
    private val app get() = PurePlusDeliveryApp.instance

    // ── data layer (repositories) ──────────────────────────────────────────────
    val authRepository by lazy { AuthRepository(app) }
    val driverRepository by lazy { DriverRepository() }
    val deliveryRepository by lazy { DeliveryRepository(app) }
    val reconciliationRepository by lazy { ReconciliationRepository(app) }
    val outboxRepository by lazy { OutboxRepository(app) }
    val tokenStore by lazy { TokenStore(app) }

    // ── domain layer (use-cases, encode the backend business rules) ─────────────
    /** Duty-shift state machine with legacy/ShiftV2 feature-detection. */
    val shiftLifecycle by lazy { ShiftLifecycle(driverRepository) }
    /** DP-12 FAILED_DELIVERY anti-abuse gate (attempt + 5-min dwell, unless refused). */
    val deliveryFailGate by lazy { DeliveryFailGate(deliveryRepository) }
    /** End-of-shift handover: declare (ShiftV2) vs close (legacy), feature-detected. */
    val reconciliationFlow by lazy { ReconciliationFlow(reconciliationRepository, driverRepository) }
    /** DP-08/09 authoritative charge breakdown + commit. */
    val deliverPreview by lazy { DeliverPreview(deliveryRepository) }
}
