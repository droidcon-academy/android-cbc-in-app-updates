package com.droidcon.inappupdates

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UpdateStore(private val context: Context) {

    val getUpdateLastCancelled: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[UPDATE_CANCELLED]?.plus(UPDATE_CANCELLED_THROTTLE) ?: 0
    }

    suspend fun setUpdateCancelled() {
        context.dataStore.edit { preferences ->
            preferences[UPDATE_CANCELLED] = System.currentTimeMillis()
        }
    }

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("update")
        private val UPDATE_CANCELLED = longPreferencesKey("update_cancelled")
        private const val UPDATE_CANCELLED_THROTTLE = 604800000 // 7 days in milliseconds
    }
}