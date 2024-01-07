package it.vfsfitvnm.compose.preferences

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val coroutineScope = CoroutineScope(Dispatchers.IO)

fun <T : Any> sharedPreferencesProperty(
    getValue: SharedPreferences.(key: String) -> T,
    setValue: SharedPreferences.Editor.(key: String, value: T) -> Unit,
    defaultValue: T
) = SharedPreferencesProperty(
    get = getValue,
    set = setValue,
    default = defaultValue
)

@Stable
data class SharedPreferencesProperty<T : Any> internal constructor(
    private val get: SharedPreferences.(key: String) -> T,
    private val set: SharedPreferences.Editor.(key: String, value: T) -> Unit,
    private val default: T
) : ReadWriteProperty<PreferencesHolder, T> {
    private val state = mutableStateOf(default)
    private var listener: OnSharedPreferenceChangeListener? = null

    override fun getValue(thisRef: PreferencesHolder, property: KProperty<*>): T {
        if (listener == null && !Snapshot.current.readOnly && !Snapshot.current.root.readOnly) {
            state.value = thisRef.get(property.name)
            listener = OnSharedPreferenceChangeListener { preferences, key ->
                if (key == property.name) preferences.get(property.name).let {
                    if (it != state.value && !Snapshot.current.readOnly) state.value = it
                }
            }
            thisRef.registerOnSharedPreferenceChangeListener(listener)
        }
        return state.value
    }

    override fun setValue(thisRef: PreferencesHolder, property: KProperty<*>, value: T) =
        coroutineScope.launch {
            thisRef.edit(commit = true) {
                set(property.name, value)
            }
        }.let { }
}

/**
 * A snapshottable, thread-safe, compose-first, extensible SharedPreferences wrapper that supports
 * virtually all types, and if it doesn't, one could simply type
 * `fun myNewType(...) = sharedPreferencesProperty(...)` and start implementing. Starts off as given
 * defaultValue until we are allowed to subscribe to SharedPreferences. Caution: the type of the
 * preference has to be [Stable], otherwise UB will occur.
 */
open class PreferencesHolder(
    application: Application,
    name: String,
    mode: Int = Context.MODE_PRIVATE
) : SharedPreferences by application.getSharedPreferences(name, mode) {
    fun boolean(defaultValue: Boolean) = sharedPreferencesProperty(
        getValue = { getBoolean(it, defaultValue) },
        setValue = { k, v -> putBoolean(k, v) },
        defaultValue
    )

    fun string(defaultValue: String) = sharedPreferencesProperty(
        getValue = { getString(it, null) ?: defaultValue },
        setValue = { k, v -> putString(k, v) },
        defaultValue
    )

    fun int(defaultValue: Int) = sharedPreferencesProperty(
        getValue = { getInt(it, defaultValue) },
        setValue = { k, v -> putInt(k, v) },
        defaultValue
    )

    fun float(defaultValue: Float) = sharedPreferencesProperty(
        getValue = { getFloat(it, defaultValue) },
        setValue = { k, v -> putFloat(k, v) },
        defaultValue
    )

    fun long(defaultValue: Long) = sharedPreferencesProperty(
        getValue = { getLong(it, defaultValue) },
        setValue = { k, v -> putLong(k, v) },
        defaultValue
    )

    inline fun <reified T : Enum<T>> enum(defaultValue: T) = sharedPreferencesProperty(
        getValue = {
            getString(it, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
                ?: defaultValue
        },
        setValue = { k, v -> putString(k, v.name) },
        defaultValue
    )

    fun stringSet(defaultValue: Set<String>) = sharedPreferencesProperty(
        getValue = { getStringSet(it, null) ?: defaultValue },
        setValue = { k, v -> putStringSet(k, v) },
        defaultValue
    )
}
