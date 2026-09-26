package com.pureplus.delivery.di

import com.pureplus.delivery.PurePlusDeliveryApp
import com.pureplus.delivery.data.local.TokenStore
import com.pureplus.delivery.data.repository.AuthRepository
import com.pureplus.delivery.data.repository.DeliveryRepository
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.data.repository.OutboxRepository
import com.pureplus.delivery.data.repository.ReconciliationRepository

/**
 * Hand-rolled service locator. This app deliberately has no DI framework (Hilt/Koin) — the
 * dependency graph is tiny, so a lazy singleton holder keeps it obvious and fast to sync.
 * ViewModels read their repositories from here (see each *ViewModel.Factory).
 */
object Deps {
    private val app get() = PurePlusDeliveryApp.instance

    val authRepository by lazy { AuthRepository(app) }
    val driverRepository by lazy { DriverRepository() }
    val deliveryRepository by lazy { DeliveryRepository(app) }
    val reconciliationRepository by lazy { ReconciliationRepository(app) }
    val outboxRepository by lazy { OutboxRepository(app) }
    val tokenStore by lazy { TokenStore(app) }
}
