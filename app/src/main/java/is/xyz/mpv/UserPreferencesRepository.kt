package `is`.xyz.mpv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Requires the dependency: androidx.datastore:datastore-preferences
// and a single app-level DataStore<Preferences> delegate (declared below).

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

/**
 * Minimal preferences repository — orientation mode only for now.
 * Expand with additional keys as more persisted settings are added.
 */
class UserPreferencesRepository(private val appContext: Context) {

    private val dataStore get() = appContext.userPreferencesDataStore

    private object Keys {
        val ORIENTATION_MODE = stringPreferencesKey("orientation_mode")
        val SUB_FONT_SIZE = intPreferencesKey("sub_font_size")
        val SUB_POS = intPreferencesKey("sub_pos")
    }

    /** Defaults to [OrientationMode.AUTO_BY_VIDEO] if unset or unparseable. */
    val orientationMode: Flow<OrientationMode> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.ORIENTATION_MODE]
        raw?.let { stored ->
            runCatching { OrientationMode.valueOf(stored) }.getOrNull()
        } ?: OrientationMode.AUTO_BY_VIDEO
    }

    val subFontSize: Flow<Int> = dataStore.data.map { it[Keys.SUB_FONT_SIZE] ?: 55 }
    val subPos: Flow<Int> = dataStore.data.map { it[Keys.SUB_POS] ?: 100 }

    suspend fun setOrientationMode(mode: OrientationMode) {
        dataStore.edit { prefs ->
            prefs[Keys.ORIENTATION_MODE] = mode.name
        }
    }

    suspend fun setSubFontSize(size: Int) {
        dataStore.edit { it[Keys.SUB_FONT_SIZE] = size }
    }

    suspend fun setSubPos(pos: Int) {
        dataStore.edit { it[Keys.SUB_POS] = pos }
    }
}
