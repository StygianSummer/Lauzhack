package tea.content

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tea.model.AttacksSection
import tea.model.Item
import tea.model.ItemCategory
import tea.model.Lesson
import tea.model.StepKey
import tea.model.TeaJson

/** One problem found in the content. [itemId] is an item id or a lesson id. */
data class ValidationIssue(
    val itemId: String,
    val rule: String,
    val message: String,
    val language: String? = null,
) {
    override fun toString(): String = "[$rule] $itemId${language?.let { " ($it)" } ?: ""}: $message"
}

data class ValidationReport(
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
) {
    val ok: Boolean get() = errors.isEmpty()

    fun errorsFor(rule: String): List<ValidationIssue> = errors.filter { it.rule == rule }
    fun warningsFor(rule: String): List<ValidationIssue> = warnings.filter { it.rule == rule }

    fun format(): String = buildString {
        appendLine("Content validation: ${errors.size} error(s), ${warnings.size} warning(s)")
        errors.forEach { appendLine("  ERROR   $it") }
        warnings.forEach { appendLine("  WARNING $it") }
    }
}

/**
 * Content rules of mvp0-plan §7.3, pure Kotlin (no I/O). Callers load the items and the deny list;
 * the jvm test `ContentValidationTest` runs this on the real `content/` folder.
 */
object ItemValidator {

    /** Rule names, used in [ValidationIssue.rule]. */
    object Rules {
        const val MISSING_TRANSLATION = "missing_translation"
        const val DENYLISTED_BRAND = "denylisted_brand"
        const val HOST_NOT_EXAMPLE = "host_not_example"
        const val HOTSPOT_TARGET_MISSING = "hotspot_target_missing"
        const val STEPS_INCOMPLETE = "steps_incomplete"
        const val STEP_CORRECT_OUT_OF_RANGE = "step_correct_out_of_range"
        const val TOO_FEW_ACTIONS = "too_few_actions"
        const val NO_SAFE_ACTION = "no_safe_action"
        const val LINK_WITHOUT_HOTSPOT = "link_without_hotspot"
        const val HELD_OUT_PAIR_REUSED = "held_out_pair_reused"
        const val PLACEMENT_TWIN_MISSING = "placement_twin_missing"

        // Structural extras (cheap, catch typos before the UI does).
        const val DUPLICATE_ID = "duplicate_id"
        const val LANGUAGE_MISMATCH = "language_mismatch"
        const val DIFFICULTY_OUT_OF_RANGE = "difficulty_out_of_range"
        const val MISSING_KIND_CONTENT = "missing_kind_content"
        const val UNKNOWN_HOTSPOT_REF = "unknown_hotspot_ref"
        const val DUPLICATE_HOTSPOT_ID = "duplicate_hotspot_id"
        const val LINK_QUESTION_OUT_OF_RANGE = "link_question_out_of_range"
        const val TWIN_NOT_FOUND = "twin_not_found"

        // Lessons.
        const val LESSON_HUNT_ITEM_MISSING = "lesson_hunt_item_missing"
        const val LESSON_HUNT_HOTSPOT_MISSING = "lesson_hunt_hotspot_missing"
    }

    private const val BASE_LANGUAGE = "en"

    /**
     * @param itemsByLang items keyed by language code (the file's language).
     * @param denylist real brands, matched case-insensitively as whole words.
     * @param strict when true, a missing translation is an error instead of a warning.
     * @param lessons lessons in any language; checked against the items of their language (fallback en).
     * @param languages languages that must have every `en` item (default: the keys of [itemsByLang]).
     */
    fun validate(
        itemsByLang: Map<String, List<Item>>,
        denylist: List<String>,
        strict: Boolean,
        lessons: List<Lesson> = emptyList(),
        languages: Collection<String> = itemsByLang.keys,
    ): ValidationReport {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        val brandPatterns = denylist.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it to Regex("(^|[^a-z0-9])${Regex.escape(it.lowercase())}([^a-z0-9]|$)", RegexOption.IGNORE_CASE) }

        // Per-language, per-item rules.
        for ((lang, items) in itemsByLang) {
            items.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
                errors += ValidationIssue(it, Rules.DUPLICATE_ID, "id appears more than once", lang)
            }
            for (item in items) {
                checkItem(item, lang, brandPatterns, errors)
            }
        }

        // One representative per id (prefer en) for the cross-item rules.
        val canonical = canonicalItems(itemsByLang)
        val byId = canonical.associateBy { it.id }

        checkTranslations(itemsByLang, languages, strict, errors, warnings)
        checkHeldOut(canonical, errors)
        checkTwins(canonical, byId, errors)

        for (lesson in lessons) checkLesson(lesson, itemsByLang, byId, errors)

        return ValidationReport(errors, warnings)
    }

    // ------------------------------------------------------------------------------------------

    private fun canonicalItems(itemsByLang: Map<String, List<Item>>): List<Item> {
        val seen = LinkedHashMap<String, Item>()
        itemsByLang[BASE_LANGUAGE]?.forEach { seen.getOrPut(it.id) { it } }
        itemsByLang.filterKeys { it != BASE_LANGUAGE }.values.flatten().forEach { seen.getOrPut(it.id) { it } }
        return seen.values.toList()
    }

    private fun checkItem(
        item: Item,
        lang: String,
        brandPatterns: List<Pair<String, Regex>>,
        errors: MutableList<ValidationIssue>,
    ) {
        fun err(rule: String, msg: String) {
            errors += ValidationIssue(item.id, rule, msg, lang)
        }

        if (item.language != lang) err(Rules.LANGUAGE_MISMATCH, "language is '${item.language}' but filed under '$lang'")
        if (item.difficulty !in 1..6) err(Rules.DIFFICULTY_OUT_OF_RANGE, "difficulty ${item.difficulty} is not in 1..6")

        // Text rules: walk every string in the item.
        for ((path, text) in textFields(item)) {
            for ((brand, re) in brandPatterns) {
                if (re.containsMatchIn(text)) err(Rules.DENYLISTED_BRAND, "real brand '$brand' in $path")
            }
            for (host in hostsIn(text)) {
                if (!isExampleHost(host)) err(Rules.HOST_NOT_EXAMPLE, "host '$host' in $path does not end in .example")
            }
        }
        item.email?.body?.forEachIndexed { i, b ->
            val href = b.href
            // A non-.example host in an href is already reported by the text walk above; here we only
            // catch hrefs that are not URLs at all (e.g. "/login"), which would hide their real host.
            if (href != null && !href.contains("{{") && hostOfUrl(href) == null) {
                err(Rules.HOST_NOT_EXAMPLE, "link href '$href' in email.body[$i] is not an absolute URL on a .example host")
            }
        }

        // Hotspots.
        item.hotspots.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            err(Rules.DUPLICATE_HOTSPOT_ID, "hotspot id '$it' is used more than once")
        }
        val hotspotIds = item.hotspots.map { it.id }.toSet()
        for (h in item.hotspots) {
            if (!targetExists(item, h.target)) err(Rules.HOTSPOT_TARGET_MISSING, "hotspot '${h.id}' targets '${h.target}', which does not exist")
        }

        // Steps.
        for ((key, step) in item.steps) {
            if (step.correct !in step.choices.indices) {
                err(Rules.STEP_CORRECT_OUT_OF_RANGE, "step '${key.name.lowercase()}' has correct=${step.correct} but ${step.choices.size} choice(s)")
            }
        }
        item.linkQuestion?.let { q ->
            if (q.correct !in q.options.indices) {
                err(Rules.LINK_QUESTION_OUT_OF_RANGE, "link_question has correct=${q.correct} but ${q.options.size} option(s)")
            }
        }

        when (item.kind) {
            "email" -> {
                val email = item.email
                if (email == null) err(Rules.MISSING_KIND_CONTENT, "kind is email but 'email' is missing")
                val missing = StepKey.entries.filter { it !in item.steps }
                if (missing.isNotEmpty()) {
                    err(Rules.STEPS_INCOMPLETE, "missing step(s): ${missing.joinToString { it.name.lowercase() }}")
                }
                if (item.actions.size < 2) err(Rules.TOO_FEW_ACTIONS, "${item.actions.size} action(s), need at least 2")
                if (item.actions.none { it.isSafe }) err(Rules.NO_SAFE_ACTION, "no action with outcome 'safe'")
                email?.body?.forEachIndexed { i, b ->
                    val ref = b.hotspot
                    if (b.t == "link" && ref == null) err(Rules.LINK_WITHOUT_HOTSPOT, "link block body[$i] has no hotspot")
                    if (ref != null && ref !in hotspotIds) {
                        err(Rules.UNKNOWN_HOTSPOT_REF, "body[$i] references hotspot '$ref', which does not exist")
                    }
                }
            }
            "news" -> if (item.news == null) err(Rules.MISSING_KIND_CONTENT, "kind is news but 'news' is missing")
            "image" -> if (item.image == null) err(Rules.MISSING_KIND_CONTENT, "kind is image but 'image' is missing")
            else -> err(Rules.MISSING_KIND_CONTENT, "unknown kind '${item.kind}'")
        }
    }

    private fun targetExists(item: Item, target: String): Boolean {
        val email = item.email ?: return false
        return when {
            target == "from_name" || target == "from_address" || target == "subject" -> true
            target == "reply_to" -> email.replyTo != null
            target == "link" -> email.body.any { it.t == "link" }
            target.startsWith("body:") -> target.removePrefix("body:").toIntOrNull()?.let { it in email.body.indices } == true
            target.startsWith("attachment:") ->
                target.removePrefix("attachment:").toIntOrNull()?.let { it in email.attachments.indices } == true
            else -> false
        }
    }

    private fun checkTranslations(
        itemsByLang: Map<String, List<Item>>,
        languages: Collection<String>,
        strict: Boolean,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>,
    ) {
        val baseIds = itemsByLang[BASE_LANGUAGE].orEmpty().map { it.id }.toSet()
        for (lang in languages.filter { it != BASE_LANGUAGE }) {
            val ids = itemsByLang[lang].orEmpty().map { it.id }.toSet()
            for (id in baseIds - ids) {
                val issue = ValidationIssue(id, Rules.MISSING_TRANSLATION, "no '$lang' version", lang)
                if (strict) errors += issue else warnings += issue
            }
        }
    }

    private fun checkHeldOut(items: List<Item>, errors: MutableList<ValidationIssue>) {
        val regular = items.filterNot { it.heldOut }
        for (h in items.filter { it.heldOut }) {
            val clash = regular.filter { it.slots.identity == h.slots.identity && it.slots.ask == h.slots.ask }
            if (clash.isNotEmpty()) {
                errors += ValidationIssue(
                    h.id, Rules.HELD_OUT_PAIR_REUSED,
                    "(identity=${h.slots.identity}, ask=${h.slots.ask}) also used by ${clash.joinToString { it.id }}",
                )
            }
        }
    }

    private fun checkTwins(items: List<Item>, byId: Map<String, Item>, errors: MutableList<ValidationIssue>) {
        for (item in items) {
            val twin = item.twinOf ?: continue
            if (twin !in byId) errors += ValidationIssue(item.id, Rules.TWIN_NOT_FOUND, "twin_of '$twin' does not exist")
        }
        for (scam in items.filter { it.kind == "email" && it.category == ItemCategory.SCAM && it.usedFor("placement") }) {
            val hasTwin = items.any { other ->
                other.category == ItemCategory.LEGIT && other.difficulty == scam.difficulty &&
                    (other.twinOf == scam.id || scam.twinOf == other.id)
            }
            if (!hasTwin) {
                errors += ValidationIssue(
                    scam.id, Rules.PLACEMENT_TWIN_MISSING,
                    "placement scam at level ${scam.difficulty} has no legit twin at the same level",
                )
            }
        }
    }

    private fun checkLesson(
        lesson: Lesson,
        itemsByLang: Map<String, List<Item>>,
        canonicalById: Map<String, Item>,
        errors: MutableList<ValidationIssue>,
    ) {
        for (section in lesson.sections.filterIsInstance<AttacksSection>()) {
            val hunt = section.hunt
            val item = itemsByLang[lesson.language]?.firstOrNull { it.id == hunt.itemId }
                ?: itemsByLang[BASE_LANGUAGE]?.firstOrNull { it.id == hunt.itemId }
                ?: canonicalById[hunt.itemId]
            if (item == null) {
                errors += ValidationIssue(lesson.id, Rules.LESSON_HUNT_ITEM_MISSING, "hunt item '${hunt.itemId}' does not exist", lesson.language)
                continue
            }
            val ids = item.hotspots.map { it.id }.toSet()
            for (flag in hunt.flags) {
                if (flag.hotspotId !in ids) {
                    errors += ValidationIssue(
                        lesson.id, Rules.LESSON_HUNT_HOTSPOT_MISSING,
                        "hunt flag '${flag.hotspotId}' is not a hotspot of '${item.id}'", lesson.language,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Text helpers (internal so tests can poke them).

    /** Every string value in the item, with a JSON-ish path, e.g. `email.body[2].v`. */
    internal fun textFields(item: Item): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        fun walk(e: JsonElement, path: String) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) -> walk(v, if (path.isEmpty()) k else "$path.$k") }
                is JsonArray -> e.forEachIndexed { i, v -> walk(v, "$path[$i]") }
                is JsonPrimitive -> if (e.isString) out += path to e.content
            }
        }
        walk(TeaJson.encodeToJsonElement(Item.serializer(), item), "")
        return out
    }

    private val urlRe = Regex("""\b[a-z][a-z0-9+.-]*://([^\s/?#"'<>()\[\]]+)""", RegexOption.IGNORE_CASE)
    private val emailRe = Regex("""[A-Za-z0-9._%+-]+@([A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+)""")
    private val wwwRe = Regex("""\bwww\.[a-z0-9-]+(?:\.[a-z0-9-]+)+""", RegexOption.IGNORE_CASE)

    /**
     * Common real TLDs, so a bare "swissalpbank.ch" in body text is caught without flagging "statement.pdf".
     * Lowercase only, so a missing space after a full stop ("bank.It") is not mistaken for a domain.
     */
    private val bareTlds = listOf(
        "com", "net", "org", "ch", "fr", "es", "de", "it", "uk", "eu", "io", "co", "info", "biz", "app", "me",
        "ly", "xyz", "online", "site", "shop", "top", "ru", "cn", "us", "ca", "at", "be", "nl", "li", "pk", "in",
    )
    private val bareRe = Regex(
        """(^|[^A-Za-z0-9@./-])((?:[a-z0-9-]+\.)+(?:${bareTlds.joinToString("|")}))(?![A-Za-z0-9-])""",
    )

    /** Hosts of URLs, e-mail addresses and bare domains in [text]. Placeholders like `{{email_domain}}` are skipped. */
    internal fun hostsIn(text: String): List<String> {
        val hosts = mutableListOf<String>()
        urlRe.findAll(text).forEach { m -> normaliseHost(m.groupValues[1])?.let(hosts::add) }
        emailRe.findAll(text).forEach { m -> hosts += m.groupValues[1].lowercase().trimEnd('.') }
        val withoutUrls = text.replace(urlRe, " ").replace(emailRe, " ")
        wwwRe.findAll(withoutUrls).forEach { m -> hosts += m.value.lowercase() }
        bareRe.findAll(withoutUrls.replace(wwwRe, " ")).forEach { m -> hosts += m.groupValues[2].lowercase() }
        return hosts.distinct()
    }

    private fun normaliseHost(authority: String): String? {
        if (authority.contains("{{")) return null
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase().trimEnd('.')
        return host.ifEmpty { null }
    }

    internal fun hostOfUrl(url: String): String? {
        if (url.startsWith("mailto:", ignoreCase = true)) {
            return url.substringAfter(':').substringAfter('@').substringBefore('?').lowercase().ifEmpty { null }
        }
        return urlRe.find(url)?.groupValues?.get(1)?.let(::normaliseHost)
    }

    internal fun isExampleHost(host: String): Boolean = host.lowercase().trimEnd('.').endsWith(".example")
}
