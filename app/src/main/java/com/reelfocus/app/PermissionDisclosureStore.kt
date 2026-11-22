package com.reelfocus.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.permissionDisclosureDataStore by preferencesDataStore(name = "permission_disclosures")

/** Persists explicit "Not now" choices without blocking access from Profile > Permissions. */
class PermissionDisclosureStore(private val context: Context) {
    fun wasDismissed(permission: String): Flow<Boolean> =
        context.permissionDisclosureDataStore.data.map { preferences ->
            preferences[booleanPreferencesKey("dismissed_${permission.lowercase()}")] ?: false
        }

    suspend fun markDismissed(permission: String) {
        context.permissionDisclosureDataStore.edit { preferences ->
            preferences[booleanPreferencesKey("dismissed_${permission.lowercase()}")] = true
        }
    }

    suspend fun clearDismissed(permission: String) {
        context.permissionDisclosureDataStore.edit { preferences ->
            preferences.remove(booleanPreferencesKey("dismissed_${permission.lowercase()}"))
        }
    }
}
