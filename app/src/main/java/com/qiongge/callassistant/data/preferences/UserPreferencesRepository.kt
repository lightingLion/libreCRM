package com.qiongge.callassistant.data.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qiongge.callassistant.domain.calling.DEFAULT_AUTOMATIC_DIAL_DELAY_SECONDS
import com.qiongge.callassistant.domain.calling.automaticDialDelayRange
import com.qiongge.callassistant.domain.greeting.FestivalCalendar
import com.qiongge.callassistant.domain.greeting.GreetingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

private val Context.userSettingsDataStore by preferencesDataStore(name = "user_settings")

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"),
}

data class CsvDirectory(val uri: Uri, val label: String)

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.userSettingsDataStore
    private val resolver = context.applicationContext.contentResolver

    private val displayNameKey = stringPreferencesKey("display_name")
    private val lastFestivalIdKey = stringPreferencesKey("last_festival_id")
    private val lastFestivalDateKey = stringPreferencesKey("last_festival_date")
    private val preferredDialSimKey = stringPreferencesKey("preferred_dial_sim")
    private val automaticDialEnabledKey = booleanPreferencesKey("automatic_dial_enabled")
    private val automaticDialDelayKey = intPreferencesKey("automatic_dial_delay_seconds")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val csvDirectoryKey = stringPreferencesKey("csv_directory")
    private val csvDirectoryLabelKey = stringPreferencesKey("csv_directory_label")

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.entries.find { it.name == preferences[themeModeKey] } ?: ThemeMode.SYSTEM
    }

    val csvDirectory: Flow<CsvDirectory?> = dataStore.data.map(::readCsvDirectory)

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setCsvDirectory(uri: Uri?) = withContext(Dispatchers.IO + NonCancellable) {
        val previous = readCsvDirectory(dataStore.data.first())
        if (uri == null) {
            dataStore.edit {
                it.remove(csvDirectoryKey)
                it.remove(csvDirectoryLabelKey)
            }
        } else {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val document = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or write)
            try {
                val label = resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                    ?.takeIf(String::isNotBlank) ?: documentId
                dataStore.edit {
                    it[csvDirectoryKey] = uri.toString()
                    it[csvDirectoryLabelKey] = label
                }
            } catch (exception: Exception) {
                if (previous?.uri != uri) releaseDirectoryPermission(uri)
                throw exception
            }
        }
        if (previous != null && previous.uri != uri) releaseDirectoryPermission(previous.uri)
    }

    private fun readCsvDirectory(preferences: Preferences): CsvDirectory? =
        preferences[csvDirectoryKey]?.let { CsvDirectory(Uri.parse(it), preferences[csvDirectoryLabelKey] ?: it) }

    private fun releaseDirectoryPermission(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider may have revoked access before the preference was cleared.
        }
    }

    val displayName: Flow<String> = dataStore.data.map { preferences ->
        preferences[displayNameKey].orEmpty()
    }
    val automaticDialEnabled: Flow<Boolean> = dataStore.data.map { it[automaticDialEnabledKey] ?: false }
    val automaticDialDelaySeconds: Flow<Int> = dataStore.data.map {
        (it[automaticDialDelayKey] ?: DEFAULT_AUTOMATIC_DIAL_DELAY_SECONDS).coerceIn(automaticDialDelayRange)
    }

    val preferredDialSim: Flow<String?> = dataStore.data.map { it[preferredDialSimKey] }

    suspend fun setPreferredDialSim(key: String?) {
        dataStore.edit { preferences ->
            if (key == null) preferences.remove(preferredDialSimKey)
            else preferences[preferredDialSimKey] = key
        }
    }

    suspend fun setAutomaticDialEnabled(enabled: Boolean) {
        dataStore.edit { it[automaticDialEnabledKey] = enabled }
    }

    suspend fun setAutomaticDialDelaySeconds(seconds: Int) {
        dataStore.edit { it[automaticDialDelayKey] = seconds.coerceIn(automaticDialDelayRange) }
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
