package tea.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tea.generated.resources.Res
import tea.model.ContentIndex
import tea.model.Item
import tea.model.Lesson
import tea.model.TeaJson

/**
 * Reads the content bundled by `:shared:syncContent` (plan §3.1) from Compose resources:
 * `files/content/index.json`, `items/<kind dir>/<id>.<lang>.json`, `lessons/<id>/lesson.<lang>.json`,
 * `resources.json` and breadth images. Missing translations fall back to `en`. Results are cached
 * per language.
 *
 * [loadBytes] receives paths like `files/content/items/email/x.en.json`; it defaults to
 * `Res.readBytes`. Tests pass a map, the server can pass a file loader.
 */
class ContentRepository(
    private val loadBytes: suspend (path: String) -> ByteArray = { Res.readBytes(it) },
    private val root: String = DEFAULT_ROOT,
) {
    private val mutex = Mutex()
    private var index: ContentIndex? = null
    private var resources: ResourceCatalog? = null
    private val itemsByLang = mutableMapOf<String, List<Item>>()
    private val lessons = mutableMapOf<Pair<String, String>, Lesson?>()

    suspend fun index(): ContentIndex = mutex.withLock { indexLocked() }

    suspend fun languages(): List<String> = index().languages

    /** Every item in [lang], each falling back to `en` when its translation is missing. Index order. */
    suspend fun items(lang: String): List<Item> = mutex.withLock {
        itemsByLang[lang]?.let { return@withLock it }
        val idx = indexLocked()
        val effective = effectiveLang(lang, idx)
        val loaded = coroutineScope {
            idx.items.map { entry ->
                async { loadWithFallback(effective) { l -> itemPath(entry, l) }?.let(::parseItem) }
            }.awaitAll().filterNotNull()
        }
        itemsByLang[lang] = loaded
        loaded
    }

    suspend fun item(id: String, lang: String): Item? = items(lang).firstOrNull { it.id == id }

    /** Lesson [id] in [lang] (fallback `en`), or null when it does not exist. */
    suspend fun lesson(id: String, lang: String): Lesson? = mutex.withLock {
        val key = id to lang
        if (key in lessons) return@withLock lessons[key]
        val effective = effectiveLang(lang, indexLocked())
        val lesson = loadWithFallback(effective) { l -> lessonPath(id, l) }?.let(::parseLesson)
        lessons[key] = lesson
        lesson
    }

    suspend fun resources(): ResourceCatalog = mutex.withLock {
        resources ?: parseResources(loadBytes("$root/resources.json")).also { resources = it }
    }

    suspend fun resource(id: String): Resource? = resources().byId(id)

    /** See [ResourceCatalog.forAsk]: country-specific entries first, then `ALL`. */
    suspend fun resourcesFor(ask: String, country: String?, verifiedOnly: Boolean = false): List<Resource> =
        resources().forAsk(ask, country, verifiedOnly)

    /** Raw bytes of a breadth image (`ImageContent.file`); decode with `decodeToImageBitmap()`. */
    suspend fun imageBytes(file: String): ByteArray = loadBytes("$root/items/breadth/img/$file")

    suspend fun clearCache() = mutex.withLock {
        index = null
        resources = null
        itemsByLang.clear()
        lessons.clear()
    }

    // ------------------------------------------------------------------------------------------

    private suspend fun indexLocked(): ContentIndex =
        index ?: parseIndex(loadBytes("$root/index.json")).also { index = it }

    private fun effectiveLang(lang: String, idx: ContentIndex): String =
        if (lang in idx.languages) lang else FALLBACK_LANGUAGE

    private fun itemPath(entry: String, lang: String) = "$root/items/$entry.$lang.json"
    private fun lessonPath(id: String, lang: String) = "$root/lessons/$id/lesson.$lang.json"

    /** Bytes for [lang], else for `en`, else null. */
    private suspend fun loadWithFallback(lang: String, path: (String) -> String): ByteArray? {
        val langs = if (lang == FALLBACK_LANGUAGE) listOf(lang) else listOf(lang, FALLBACK_LANGUAGE)
        for (l in langs) {
            try {
                return loadBytes(path(l))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // missing file: try the next language
            }
        }
        return null
    }

    companion object {
        const val DEFAULT_ROOT = "files/content"
        const val FALLBACK_LANGUAGE = "en"

        fun parseIndex(bytes: ByteArray): ContentIndex = TeaJson.decodeFromString(ContentIndex.serializer(), bytes.decodeToString())
        fun parseItem(bytes: ByteArray): Item = TeaJson.decodeFromString(Item.serializer(), bytes.decodeToString())
        fun parseLesson(bytes: ByteArray): Lesson = TeaJson.decodeFromString(Lesson.serializer(), bytes.decodeToString())
        fun parseResources(bytes: ByteArray): ResourceCatalog =
            TeaJson.decodeFromString(ResourceCatalog.serializer(), bytes.decodeToString())
    }
}
