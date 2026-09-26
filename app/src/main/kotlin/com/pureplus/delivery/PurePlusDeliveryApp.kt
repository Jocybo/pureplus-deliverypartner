package com.pureplus.delivery

import android.app.Application
import com.pureplus.delivery.data.local.AppDatabase
import com.pureplus.delivery.data.remote.RetrofitClient
import com.pureplus.delivery.data.sync.SyncWorker

class PurePlusDeliveryApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    val retrofitClient by lazy { RetrofitClient }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Flush the offline outbox periodically whenever connectivity is available.
        SyncWorker.schedulePeriodicOnConnectivity(this)
    }

    companion object {
        lateinit var instance: PurePlusDeliveryApp
            private set
    }
}
