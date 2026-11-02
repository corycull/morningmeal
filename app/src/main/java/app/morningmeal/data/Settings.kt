package app.morningmeal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    /** 0 means no goal set. */
    val dailyGoal: Int = 0,
    val theme: ThemeMode = ThemeMode.System,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val goalKey = intPreferencesKey("daily_goal")
    private val themeKey = stringPreferencesKey("theme")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            dailyGoal = prefs[goalKey] ?: 0,
            theme = prefs[themeKey]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.System,
        )
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { it[goalKey] = goal.coerceIn(0, 20_000) }
    }

    suspend fun setTheme(theme: ThemeMode) {
        context.dataStore.edit { it[themeKey] = theme.name }
    }
}
