package tea.store

import android.content.Context
import android.content.SharedPreferences
import tea.android.TeaAndroid

/**
 * Android store: one private SharedPreferences file. `apply()` updates the in-memory map at once
 * and writes to disk in the background, which is what [KeyValueStore] promises.
 */
class SharedPreferencesKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        if (prefs.getString(key, null) != value) prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        if (prefs.contains(key)) prefs.edit().remove(key).apply()
    }

    override fun keys(): Set<String> = prefs.all.keys.toSet()

    companion object {
        const val FILE = "thisistea_store"
    }
}

actual fun platformKeyValueStore(): KeyValueStore = SharedPreferencesKeyValueStore(
    TeaAndroid.context.getSharedPreferences(SharedPreferencesKeyValueStore.FILE, Context.MODE_PRIVATE),
)
