package tea.store

import kotlinx.browser.localStorage

/** Web store: `window.localStorage`, keys used as given (all ThisIsTea keys start with `tea.`). */
class LocalStorageKeyValueStore : KeyValueStore {
    override fun get(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull()

    override fun put(key: String, value: String) {
        runCatching { localStorage.setItem(key, value) }
    }

    override fun remove(key: String) {
        runCatching { localStorage.removeItem(key) }
    }

    override fun keys(): Set<String> = runCatching {
        (0 until localStorage.length).mapNotNull { localStorage.key(it) }.toSet()
    }.getOrDefault(emptySet())
}

actual fun platformKeyValueStore(): KeyValueStore = LocalStorageKeyValueStore()
