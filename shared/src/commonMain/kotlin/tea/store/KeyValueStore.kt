package tea.store

/**
 * A small persistent string map (plan §3: web = localStorage, desktop = a JSON file under
 * `~/.thisistea/`, Android later = SharedPreferences). Values are JSON strings written by
 * [LocalState]. Writes are immediate (no batching): the data is small.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>

    /** Removes every key starting with [prefix]. */
    fun clear(prefix: String = "") {
        keys().filter { it.startsWith(prefix) }.forEach { remove(it) }
    }
}

/** The platform store (localStorage on web, `~/.thisistea/store.json` on desktop). */
expect fun platformKeyValueStore(): KeyValueStore

/** For tests and previews. */
class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val map = LinkedHashMap(initial)
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun keys(): Set<String> = map.keys.toSet()
    fun snapshot(): Map<String, String> = map.toMap()
}
