package com.pureplus.delivery.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "driver_prefs")

class TokenStore(private val context: Context) {

    companion object {
        private val KEY_TOKEN       = stringPreferencesKey("access_token")
        private val KEY_EMPLOYEE_ID = longPreferencesKey("employee_id")
        private val KEY_STORE_ID    = longPreferencesKey("store_id")
        private val KEY_FULL_NAME   = stringPreferencesKey("full_name")
        private val KEY_ROLE        = stringPreferencesKey("role")
    }

    suspend fun saveSession(
        token: String,
        employeeId: Long?,
        storeId: Int?,
        fullName: String?,
        role: String?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN]       = token
            prefs[KEY_EMPLOYEE_ID] = employeeId ?: -1L
            prefs[KEY_STORE_ID]    = storeId?.toLong() ?: -1L
            prefs[KEY_FULL_NAME]   = fullName ?: ""
            prefs[KEY_ROLE]        = role ?: ""
        }
    }

    suspend fun getToken(): String? =
        context.dataStore.data.map { it[KEY_TOKEN] }.firstOrNull()

    suspend fun getEmployeeId(): Long =
        context.dataStore.data.map { it[KEY_EMPLOYEE_ID] ?: -1L }.firstOrNull() ?: -1L

    suspend fun getStoreId(): Int =
        context.dataStore.data.map { it[KEY_STORE_ID]?.toInt() ?: 1 }.firstOrNull() ?: 1

    suspend fun getFullName(): String =
        context.dataStore.data.map { it[KEY_FULL_NAME] ?: "" }.firstOrNull() ?: ""

    suspend fun isLoggedIn(): Boolean = getToken()?.isNotBlank() == true

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
