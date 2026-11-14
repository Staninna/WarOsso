package io.github.staninna.warosso.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** Switch between camera and map by how the phone is held. Off shows a manual toggle. */
    val autoSwitchView: Boolean = true,
    val vibrateOnArrival: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The settings file. [settings] is null only for the moment it takes to read it at startup. */
class SettingsRepository(private val store: DataStore<Preferences>, private val scope: CoroutineScope) {

    val settings: StateFlow<Settings?> = store.data.map { it.toSettings() }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The settings as they are now, defaults until the file is read. */
    val current: Settings get() = settings.value ?: Settings()

    fun update(change: (Settings) -> Settings) {
        scope.launch {
            store.edit { preferences ->
                val next = change(preferences.toSettings())
                preferences[AUTO_SWITCH] = next.autoSwitchView
                preferences[VIBRATE] = next.vibrateOnArrival
                preferences[THEME] = next.theme.name
            }
        }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            autoSwitchView = this[AUTO_SWITCH] ?: defaults.autoSwitchView,
            vibrateOnArrival = this[VIBRATE] ?: defaults.vibrateOnArrival,
            theme = this[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.theme,
        )
    }

    private companion object {
        val AUTO_SWITCH = booleanPreferencesKey("auto_switch_view")
        val VIBRATE = booleanPreferencesKey("vibrate_on_arrival")
        val THEME = stringPreferencesKey("theme")
    }
}

/** The app language. Android stores the override itself, so there is nothing to persist here. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    DUTCH("nl"),
    ;

    fun apply(context: Context) {
        val locales = tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }

    companion object {
        fun current(context: Context): AppLanguage {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            val language = if (locales.isEmpty) null else locales[0].language
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
