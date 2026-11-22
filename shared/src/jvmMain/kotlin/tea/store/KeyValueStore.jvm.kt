package tea.store

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Desktop store: one JSON object in `<home>/.thisistea/store.json` (override the folder with the
 * system property `tea.home` or the env var `TEA_HOME`). Rewritten atomically on every change.
 */
class FileKeyValueStore(private val file: File) : KeyValueStore {
    private val lock = Any()
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val map: MutableMap<String, String> = LinkedHashMap(
        runCatching { kotlinx.serialization.json.Json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap()),
    )

    override fun get(key: String): String? = synchronized(lock) { map[key] }

    override fun put(key: String, value: String) = synchronized(lock) {
        if (map[key] == value) return
        map[key] = value
        write()
    }

    override fun remove(key: String) = synchronized(lock) {
        if (map.remove(key) != null) write()
    }

    override fun keys(): Set<String> = synchronized(lock) { map.keys.toSet() }

    private fun write() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(kotlinx.serialization.json.Json.encodeToString(serializer, map))
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        fun defaultDir(): File {
            val override = System.getProperty("tea.home") ?: System.getenv("TEA_HOME")
            return if (!override.isNullOrBlank()) File(override) else File(System.getProperty("user.home"), ".thisistea")
        }
    }
}

actual fun platformKeyValueStore(): KeyValueStore = FileKeyValueStore(File(FileKeyValueStore.defaultDir(), "store.json"))
