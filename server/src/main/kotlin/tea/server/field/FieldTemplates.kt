package tea.server.field

import java.io.File
import org.slf4j.LoggerFactory
import tea.model.FieldReveal
import tea.model.FieldTemplate
import tea.model.StepKey
import tea.model.TeaJson

/**
 * Practice-email templates (plan §12): `CONTENT_DIR/field/<template>.<lang>.json`, falling back to
 * `.en.json`, then to a built-in placeholder. Files are re-read on every use so the human-written
 * template can be dropped in without a restart.
 */
class FieldTemplates(contentDir: String) {
    private val log = LoggerFactory.getLogger(FieldTemplates::class.java)
    private val dir = File(contentDir, "field")
    private val safeId = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
    private val safeLang = Regex("^[a-z]{2}$")

    fun load(templateId: String, language: String): FieldTemplate {
        if (safeId.matches(templateId)) {
            val langs = listOf(language.lowercase().take(2), "en").filter { safeLang.matches(it) }.distinct()
            for (lang in langs) {
                val f = File(dir, "$templateId.$lang.json")
                if (!f.isFile) continue
                try {
                    return TeaJson.decodeFromString(FieldTemplate.serializer(), f.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    log.warn("field template {} is invalid: {}", f.name, e.message)
                }
            }
        }
        return placeholder(templateId)
    }

    data class RenderedMail(val subject: String, val text: String, val html: String?)

    fun render(
        t: FieldTemplate,
        firstName: String,
        clickUrl: String,
        settingsUrl: String,
        lessonUrl: String = "",
    ): RenderedMail {
        fun fill(s: String, escape: Boolean): String {
            val name = if (escape) htmlEscape(firstName) else firstName
            val click = if (escape) htmlEscape(clickUrl) else clickUrl
            val settings = if (escape) htmlEscape(settingsUrl) else settingsUrl
            val lesson = if (escape) htmlEscape(lessonUrl) else lessonUrl
            return s.replace("{{first_name}}", name)
                .replace("{{click_url}}", click)
                .replace("{{settings_url}}", settings)
                .replace("{{lesson_url}}", lesson)
        }
        return RenderedMail(fill(t.subject, false), fill(t.text, false), t.html?.let { fill(it, true) })
    }

    companion object {
        fun htmlEscape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

        /** Built-in placeholder until the human-written template lands in content/field/. */
        fun placeholder(templateId: String) = FieldTemplate(
            id = templateId,
            subject = "Placeholder practice email",
            text = """
                |Hi {{first_name}},
                |
                |Placeholder practice email: replace before the demo.
                |
                |This is the ThisIsTea practice email you asked for. Open this link to see how you did:
                |{{click_url}}
                |
                |Manage ThisIsTea emails: {{settings_url}}
            """.trimMargin(),
            html = null,
            reveal = FieldReveal(
                steps = mapOf(
                    StepKey.WHO to "Placeholder: who was really writing?",
                    StepKey.EMOTION to "Placeholder: what feeling was pushed?",
                    StepKey.ASK to "Placeholder: what did it want you to do?",
                    StepKey.VERIFY to "Placeholder: how could you check through a channel you control?",
                ),
                hints = listOf("Placeholder practice email: replace before the demo."),
            ),
        )
    }
}
