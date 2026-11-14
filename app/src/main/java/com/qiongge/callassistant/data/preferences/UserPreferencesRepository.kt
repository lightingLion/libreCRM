package com.qiongge.callassistant.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qiongge.callassistant.domain.greeting.FestivalCalendar
import com.qiongge.callassistant.domain.greeting.GreetingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

private val Context.userSettingsDataStore by preferencesDataStore(name = "user_settings")

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.userSettingsDataStore

    private val displayNameKey = stringPreferencesKey("display_name")
    private val lastFestivalIdKey = stringPreferencesKey("last_festival_id")
    private val lastFestivalDateKey = stringPreferencesKey("last_festival_date")
    private val automaticDialEnabledKey = booleanPreferencesKey("automatic_dial_enabled")
    private val automaticDialDelayKey = intPreferencesKey("automatic_dial_delay_seconds")

    val displayName: Flow<String> = dataStore.data.map { preferences ->
        preferences[displayNameKey].orEmpty()
    }
    val automaticDialEnabled: Flow<Boolean> = dataStore.data.map { it[automaticDialEnabledKey] ?: false }
    val automaticDialDelaySeconds: Flow<Int> = dataStore.data.map { it[automaticDialDelayKey] ?: 5 }

    suspend fun setAutomaticDialEnabled(enabled: Boolean) {
        dataStore.edit { it[automaticDialEnabledKey] = enabled }
    }

    suspend fun setAutomaticDialDelaySeconds(seconds: Int) {
        dataStore.edit { it[automaticDialDelayKey] = seconds.coerceIn(5, 30) }
    }

    suspend fun setDisplayName(displayName: String) {
        dataStore.edit { preferences ->
            val value = displayName.trim()
            if (value.isEmpty()) {
                preferences.remove(displayNameKey)
            } else {
                preferences[displayNameKey] = value
            }
        }
    }

    suspend fun greetingForMenu(now: LocalDateTime = LocalDateTime.now()): String {
        var message = GreetingPolicy.regularGreeting(now, displayName = null)
        val festival = FestivalCalendar.forDate(now.toLocalDate())
        dataStore.edit { preferences ->
            val resolution = GreetingPolicy.resolveMenuGreeting(
                now = now,
                displayName = preferences[displayNameKey],
                lastFestivalId = preferences[lastFestivalIdKey],
                lastFestivalDate = preferences[lastFestivalDateKey],
                festival = festival,
            )
            message = resolution.message
            resolution.festivalIdToRecord?.let { preferences[lastFestivalIdKey] = it }
            resolution.festivalDateToRecord?.let { preferences[lastFestivalDateKey] = it }
        }
        return message
    }
}
