package tea.content

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import tea.model.FieldTemplate
import tea.model.Item
import tea.model.Lesson
import tea.model.TeaJson
import tea.model.ToolsSection

/**
 * Validates the real `content/` folder (plan §7.3). Run with `./gradlew :shared:jvmTest`
 * (add `-Pstrict=true` to make missing translations fail). Gradle passes the folder as `tea.contentDir`.
 */
class ContentValidationTest {

    private val contentDir: File = System.getProperty("tea.contentDir")?.let(::File)
        ?: generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "content") }
            .first { File(it, "languages.txt").isFile }
    private val strict = System.getProperty("tea.strict")?.toBoolean() ?: false

    private val fileName = Regex("""^(.+)\.([a-z]{2,3}(?:-[A-Za-z]{2})?)\.json$""")

    private fun lines(name: String) = File(contentDir, name).readLines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    @Test
    fun contentFolderIsValid() {
        val problems = mutableListOf<String>()
        val itemsByLang = mutableMapOf<String, MutableList<Item>>()

        File(contentDir, "items").walkTopDown()
            .filter { it.isFile && it.extension == "json" && !it.name.startsWith("_") }
            .forEach { f ->
                val m = fileName.matchEntire(f.name)
                if (m == null) {
                    problems += "${f.relativeTo(contentDir)}: file name must be <id>.<lang>.json"
                    return@forEach
                }
                val (id, lang) = m.destructured
                val item = try {
                    ContentRepository.parseItem(f.readBytes())
                } catch (e: Exception) {
                    problems += "${f.relativeTo(contentDir)}: cannot parse: ${e.message}"
                    return@forEach
                }
                if (item.id != id) problems += "${f.relativeTo(contentDir)}: id '${item.id}' does not match file name"
                val kindDir = f.parentFile.relativeTo(File(contentDir, "items")).invariantSeparatorsPath
                val expectedDir = if (item.kind == "email") "email" else "breadth"
                if (kindDir != expectedDir) problems += "${f.relativeTo(contentDir)}: kind '${item.kind}' belongs in items/$expectedDir/"
                itemsByLang.getOrPut(lang) { mutableListOf() } += item
            }

        val lessons = mutableListOf<Lesson>()
        File(contentDir, "lessons").walkTopDown().filter { it.isFile && it.name.startsWith("lesson.") }.forEach { f ->
            try {
                lessons += ContentRepository.parseLesson(f.readBytes())
            } catch (e: Exception) {
                problems += "${f.relativeTo(contentDir)}: cannot parse: ${e.message}"
            }
        }

        val catalog = ContentRepository.parseResources(File(contentDir, "resources/resources.json").readBytes())
        for (lesson in lessons) {
            for (tool in lesson.sections.filterIsInstance<ToolsSection>().flatMap { it.tools }) {
                if (catalog.byId(tool.resourceId) == null) {
                    problems += "lesson ${lesson.id} (${lesson.language}): tool '${tool.resourceId}' is not in resources.json"
                }
            }
        }

        File(contentDir, "field").listFiles { f -> f.extension == "json" }.orEmpty().forEach { f ->
            try {
                val t = TeaJson.decodeFromString(FieldTemplate.serializer(), f.readText())
                if ("{{lesson_url}}" !in t.text && "{{click_url}}" !in t.text) {
                    problems += "field/${f.name}: text has no {{click_url}}"
                }
                if ("{{settings_url}}" !in t.text) problems += "field/${f.name}: text has no {{settings_url}}"
            } catch (e: Exception) {
                problems += "field/${f.name}: cannot parse: ${e.message}"
            }
        }

        val report = ItemValidator.validate(
            itemsByLang = itemsByLang,
            denylist = lines("denylist.txt"),
            strict = strict,
            lessons = lessons,
            languages = lines("languages.txt"),
        )
        println(report.format())
        problems.forEach { println("  ERROR   $it") }
        if (!report.ok || problems.isNotEmpty()) {
            fail("Content is invalid (strict=$strict):\n${report.format()}${problems.joinToString("\n") { "  ERROR   $it" }}")
        }
    }

    @Test
    fun seedPairIsPresent() {
        val dir = File(contentDir, "items/email")
        val scam = ContentRepository.parseItem(File(dir, "email-bank-login-01.en.json").readBytes())
        val legit = ContentRepository.parseItem(File(dir, "email-bank-code-unsolicited-01.en.json").readBytes())
        assertTrue(scam.isScam && !legit.isScam)
        assertTrue(legit.unsolicited)
        assertEquals(scam.id, legit.twinOf)
        assertEquals(scam.seed, legit.seed)
    }
}
