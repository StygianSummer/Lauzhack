package tea.server.leaderboard

import java.io.File
import org.slf4j.LoggerFactory
import tea.model.Item
import tea.model.TeaJson

/**
 * Items used to recompute XP on the server (plan §6.9): every `CONTENT_DIR/items/<kind>/<id>.en.json`
 * (any depth),
 * loaded once at start. A missing folder or a bad file only means fewer known items
 * (Xp.xpFor then counts the verdict only).
 */
class ItemCatalog(private val items: Map<String, Item>) {
    operator fun get(id: String): Item? = items[id]
    val size: Int get() = items.size

    companion object {
        private val log = LoggerFactory.getLogger(ItemCatalog::class.java)

        fun load(contentDir: String): ItemCatalog {
            val root = File(contentDir, "items")
            if (!root.isDirectory) {
                log.warn("no items folder at {}: leaderboard XP counts verdicts only", root.path)
                return ItemCatalog(emptyMap())
            }
            val items = root.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".en.json") }
                .mapNotNull { f ->
                    runCatching { TeaJson.decodeFromString(Item.serializer(), f.readText(Charsets.UTF_8)) }
                        .onFailure { log.warn("skipping item {}: {}", f.name, it.message?.take(200)) }
                        .getOrNull()
                }
                .associateBy { it.id }
            log.info("loaded {} items for XP", items.size)
            return ItemCatalog(items)
        }
    }
}
