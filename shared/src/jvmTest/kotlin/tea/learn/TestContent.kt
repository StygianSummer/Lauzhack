package tea.learn

import java.io.File
import kotlinx.coroutines.runBlocking
import tea.content.ContentRepository
import tea.model.Item
import tea.model.Lesson

/**
 * The real `content/` folder served to a [ContentRepository] straight from disk (no Gradle sync
 * needed): `files/content/index.json` is built from the folder like `:shared:syncContent` does.
 */
object TestContent {
    val dir: File = System.getProperty("tea.contentDir")?.let(::File)
        ?: generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "content") }
            .first { File(it, "languages.txt").isFile }

    private val fileName = Regex("""^(.+)\.([a-z]{2,3}(?:-[A-Za-z]{2})?)\.json$""")

    fun repository(): ContentRepository = ContentRepository(loadBytes = { path -> load(path) })

    fun items(lang: String = "en"): List<Item> = runBlocking { repository().items(lang) }

    fun lesson(id: String = "email-01", lang: String = "en"): Lesson = runBlocking { repository().lesson(id, lang)!! }

    fun load(path: String): ByteArray {
        val rel = path.removePrefix("${ContentRepository.DEFAULT_ROOT}/")
        return when (rel) {
            "index.json" -> index().encodeToByteArray()
            "resources.json" -> File(dir, "resources/resources.json").readBytes()
            else -> File(dir, rel).readBytes()
        }
    }

    private fun index(): String {
        val itemsDir = File(dir, "items")
        val items = itemsDir.walkTopDown()
            .filter { it.isFile && it.parentFile.name != "img" }
            .mapNotNull { f ->
                val m = fileName.matchEntire(f.name) ?: return@mapNotNull null
                if (m.groupValues[1].startsWith("_")) return@mapNotNull null
                f.parentFile.relativeTo(itemsDir).invariantSeparatorsPath + "/" + m.groupValues[1]
            }
            .distinct().sorted().toList()
        val lessons = File(dir, "lessons").listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()
        val languages = File(dir, "languages.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        fun arr(xs: List<String>) = xs.joinToString(",", "[", "]") { "\"$it\"" }
        return """{"items":${arr(items)},"lessons":${arr(lessons)},"languages":${arr(languages)}}"""
    }
}
