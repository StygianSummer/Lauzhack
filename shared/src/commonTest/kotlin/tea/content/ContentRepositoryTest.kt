package tea.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import tea.model.Item
import tea.model.Lesson
import tea.model.ReadinessSection
import tea.model.TeaJson

class ContentRepositoryTest {

    private val root = "files/content"

    private fun itemJson(id: String, lang: String, subject: String) =
        TeaJson.encodeToString(Item.serializer(), Fixtures.scam(id = id).let {
            it.copy(language = lang, email = it.email!!.copy(subject = subject))
        })

    private fun lessonJson(lang: String, title: String) = TeaJson.encodeToString(
        Lesson.serializer(),
        Lesson("email-01", lang, title, sections = listOf(ReadinessSection("p", "b", "d"))),
    )

    private val files: Map<String, String> = mapOf(
        "$root/index.json" to """{ "items": ["email/a", "email/b"], "lessons": ["email-01"], "languages": ["en","fr","es"] }""",
        "$root/items/email/a.en.json" to itemJson("a", "en", "Hello"),
        "$root/items/email/a.fr.json" to itemJson("a", "fr", "Bonjour"),
        "$root/items/email/b.en.json" to itemJson("b", "en", "Only English"),
        "$root/lessons/email-01/lesson.en.json" to lessonJson("en", "Phishing"),
        "$root/resources.json" to """
            { "_comment": "x", "resources": [
              { "id": "g1", "name": "Global", "url": "https://g.example", "countries": ["ALL"], "asks": ["click_login"], "purpose": "p", "verified": false },
              { "id": "ch1", "name": "Swiss", "url": "https://c.example", "countries": ["CH"], "asks": ["click_login", "pay_fee"], "purpose": "p", "verified": true },
              { "id": "fee", "name": "Fee", "url": "", "countries": ["ALL"], "asks": ["pay_fee"], "purpose": "p", "verified": false }
            ] }
        """.trimIndent(),
        "$root/items/breadth/img/x.webp" to "IMG",
    )

    private val requested = mutableListOf<String>()

    private fun repo() = ContentRepository(loadBytes = { path ->
        requested += path
        files[path]?.encodeToByteArray() ?: throw IllegalStateException("missing $path")
    })

    @Test
    fun itemsFallBackToEnglishPerItem() = runTest {
        val items = repo().items("fr")
        assertEquals(listOf("a", "b"), items.map { it.id })
        assertEquals("Bonjour", items[0].email!!.subject)
        assertEquals("fr", items[0].language)
        assertEquals("Only English", items[1].email!!.subject)
        assertEquals("en", items[1].language)
    }

    @Test
    fun unknownLanguageUsesEnglish() = runTest {
        val r = repo()
        assertEquals("Hello", r.item("a", "de")!!.email!!.subject)
        assertEquals(false, requested.any { it.endsWith(".de.json") })
    }

    @Test
    fun itemsAreCachedPerLanguage() = runTest {
        val r = repo()
        r.items("en")
        val before = requested.size
        r.items("en")
        assertEquals(before, requested.size)
        r.items("fr")
        assertEquals(true, requested.size > before)
    }

    @Test
    fun lessonFallbackAndMissing() = runTest {
        val r = repo()
        assertEquals("Phishing", r.lesson("email-01", "es")!!.title)
        assertNull(r.lesson("email-99", "en"))
    }

    @Test
    fun resourcesForAskAndCountry() = runTest {
        val r = repo()
        assertEquals(listOf("ch1", "g1"), r.resourcesFor("click_login", "ch").map { it.id })
        assertEquals(listOf("g1"), r.resourcesFor("click_login", "FR").map { it.id })
        assertEquals(listOf("g1"), r.resourcesFor("click_login", null).map { it.id })
        assertEquals(listOf("ch1"), r.resourcesFor("click_login", "CH", verifiedOnly = true).map { it.id })
        assertEquals(false, r.resource("fee")!!.hasUrl)
    }

    @Test
    fun imageBytesAndMissingFiles() = runTest {
        val r = repo()
        assertEquals("IMG", r.imageBytes("x.webp").decodeToString())
        assertFailsWith<IllegalStateException> { r.imageBytes("nope.webp") }
    }
}
