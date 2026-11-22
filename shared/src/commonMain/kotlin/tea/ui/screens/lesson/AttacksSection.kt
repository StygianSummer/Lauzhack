package tea.ui.screens.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.Screen
import tea.app.TeaApp
import tea.generated.resources.Res
import tea.generated.resources.lesson_attacks_caught
import tea.generated.resources.lesson_attacks_title
import tea.generated.resources.lesson_back_to_rules
import tea.generated.resources.lesson_hunt_decoy
import tea.generated.resources.lesson_hunt_done_intro
import tea.generated.resources.lesson_hunt_done_title
import tea.generated.resources.lesson_hunt_finished
import tea.generated.resources.lesson_hunt_found
import tea.generated.resources.lesson_hunt_gave_up
import tea.generated.resources.lesson_hunt_locked_body
import tea.generated.resources.lesson_hunt_locked_title
import tea.generated.resources.lesson_hunt_missed
import tea.generated.resources.lesson_hunt_perfect
import tea.generated.resources.lesson_hunt_show_rest
import tea.generated.resources.lesson_hunt_start
import tea.generated.resources.lesson_hunt_title
import tea.generated.resources.lesson_hunt_wrong
import tea.generated.resources.lesson_recovery_progress
import tea.generated.resources.lesson_recovery_role
import tea.generated.resources.lesson_spot_difference
import tea.model.AttacksSection
import tea.model.ContentSection
import tea.model.Hunt
import tea.model.Item
import tea.model.Lesson
import tea.model.Mood
import tea.model.Persona
import tea.ui.components.DomainDiff
import tea.ui.components.EmailView
import tea.ui.components.PersonaBubble
import tea.ui.components.PersonaCard
import tea.ui.components.PersonaTitle
import tea.ui.components.SectionHeading
import tea.ui.components.SpiderPanel
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCheckbox
import tea.ui.components.VoiceLine
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.practice.rememberFilled
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/** What Spider says under the hunt. */
sealed interface HuntMessage {
    data object Start : HuntMessage
    data object Decoy : HuntMessage
    data object GaveUp : HuntMessage
    data object Perfect : HuntMessage
    data object Finished : HuntMessage
    /** A hotspot's own explanation (a found flag, a decoy hotspot, or a sign the hunt does not count). */
    data class Explain(val text: String) : HuntMessage
}

/**
 * State of Spider's hunt (plan §9.4): tap the flagged hotspots of `hunt.itemId`. Taps on a flag
 * find it (and unlock Spider's line for it); taps on anything that is not a red flag (decoys, plain
 * hotspots) are wrong guesses; taps on a red-flag hotspot that is not one of the hunt's flags are
 * neither (Spider explains it, nothing is counted). "Show me the rest" gives up.
 */
@Stable
class HuntUi(val hunt: Hunt) {
    val flagIds: List<String> = hunt.flags.map { it.hotspotId }
    var found: Set<String> by mutableStateOf(emptySet())
        private set
    var misses: Int by mutableStateOf(0)
        private set
    var over: Boolean by mutableStateOf(false)
        private set
    var gaveUp: Boolean by mutableStateOf(false)
        private set
    var message: HuntMessage by mutableStateOf(HuntMessage.Start)
        private set

    fun tap(regionId: String, item: Item) {
        if (over) return
        val hotspot = item.hotspots.firstOrNull { it.id == regionId }
        if (regionId in flagIds) {
            if (regionId in found) return
            found = found + regionId
            message = HuntMessage.Explain(hotspot?.explain ?: hunt.flags.first { it.hotspotId == regionId }.spiderSays)
            if (found.size == flagIds.size) {
                over = true
                message = if (misses == 0) HuntMessage.Perfect else HuntMessage.Finished
            }
            return
        }
        if (hotspot != null && hotspot.redFlag) {
            message = HuntMessage.Explain(hotspot.explain)
            return
        }
        misses++
        message = if (hotspot != null) HuntMessage.Explain(hotspot.explain) else HuntMessage.Decoy
    }

    fun giveUp() {
        if (over) return
        over = true
        gaveUp = true
        message = HuntMessage.GaveUp
    }

    /** Regions to highlight: found flags, and after the end every flag. */
    fun highlighted(): Set<String> = if (over) flagIds.toSet() else found
}

/**
 * The attacks section: Spider's dark panel with the hunt, the domain difference and Cat's
 * debrief after it, then Turtle's recovery checklist (ticks persisted under
 * `tea.recovery.<lesson id>`; title "Caught" only if a `field_clicked` event exists).
 */
@Composable
fun ColumnScope.AttacksSectionView(app: TeaApp, lesson: Lesson, s: AttacksSection, number: Int, hunt: HuntUi = remember(s.hunt) { HuntUi(s.hunt) }) {
    val lang by app.lang.collectAsState()
    val caught by app.fieldClicked.collectAsState()
    val raw by produceState<Item?>(null, s.hunt.itemId, lang) {
        value = runCatching { app.content.item(s.hunt.itemId, lang) }.getOrNull()
    }

    SpiderPanel {
        SectionHeading(stringResource(Res.string.lesson_attacks_title), number = number)
        if (caught) PersonaBubble(Persona.SPIDER, Mood.SMUG, stringResource(Res.string.lesson_attacks_caught))
        PersonaTitle(Persona.SPIDER, stringResource(if (hunt.over) Res.string.lesson_hunt_done_title else Res.string.lesson_hunt_title))
        Text(
            if (hunt.over) stringResource(Res.string.lesson_hunt_done_intro) else s.hunt.intro,
            style = TeaTheme.type.small,
            color = TeaColors.webSoft,
        )
        val base = raw
        if (base != null) {
            val item = rememberFilled(app, base)
            item.email?.let { email ->
                EmailView(
                    email,
                    item.hotspots,
                    tappable = !hunt.over,
                    highlighted = hunt.highlighted(),
                    onTap = { hunt.tap(it, item) },
                    decoys = true,
                    now = app.now(),
                )
            }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.lesson_hunt_found, hunt.found.size, hunt.flagIds.size) + " · " +
                    pluralStringResource(Res.plurals.lesson_hunt_wrong, hunt.misses, hunt.misses),
                style = TeaTheme.type.smallStrong,
                color = TeaColors.webInk,
            )
            if (!hunt.over) {
                HuntTextButton(stringResource(Res.string.lesson_hunt_show_rest)) { hunt.giveUp() }
            }
        }
        PersonaBubble(
            Persona.SPIDER,
            Mood.SMUG,
            when (val m = hunt.message) {
                HuntMessage.Start -> stringResource(Res.string.lesson_hunt_start)
                HuntMessage.Decoy -> stringResource(Res.string.lesson_hunt_decoy)
                HuntMessage.GaveUp -> stringResource(Res.string.lesson_hunt_gave_up)
                HuntMessage.Perfect -> stringResource(Res.string.lesson_hunt_perfect)
                HuntMessage.Finished -> stringResource(Res.string.lesson_hunt_finished)
                is HuntMessage.Explain -> m.text
            },
        )
        s.hunt.flags.forEachIndexed { i, flag ->
            val isFound = flag.hotspotId in hunt.found
            val unlocked = isFound || hunt.over
            FlagCard(
                title = if (unlocked) flag.title else stringResource(Res.string.lesson_hunt_locked_title, i + 1),
                body = if (unlocked) flag.spiderSays else stringResource(Res.string.lesson_hunt_locked_body),
                note = if (hunt.over && !isFound) stringResource(Res.string.lesson_hunt_missed) else null,
                locked = !unlocked,
            )
        }
        if (hunt.over) {
            base?.let { LookAlike.pair(it) }?.let { (real, fake) ->
                Text(stringResource(Res.string.lesson_spot_difference), style = TeaTheme.type.title, color = TeaColors.webInk)
                DomainDiff(real, fake)
            }
            PersonaBubble(Persona.CAT, Mood.NEUTRAL, s.hunt.debrief)
            val rulesIndex = lesson.sections.indexOfFirst { it is ContentSection }
            if (rulesIndex >= 0) {
                HuntTextButton(stringResource(Res.string.lesson_back_to_rules)) { app.navigate(Screen.Lesson(lesson.id, rulesIndex)) }
            }
        }
    }

    RecoveryCard(app, lesson.id, s, caught)
}

/** A light text button that stays readable on Spider's dark panel. */
@Composable
private fun HuntTextButton(text: String, onClick: () -> Unit) {
    TeaButton(text, onClick, variant = TeaButtonVariant.Secondary, small = true)
}

@Composable
private fun FlagCard(title: String, body: String, note: String?, locked: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(TeaShapes.md).background(TeaColors.webCard)
            .border(TeaSize.border, if (locked) TeaColors.webLine else TeaColors.spider, TeaShapes.md)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = TeaTheme.type.bodyStrong, color = if (locked) TeaColors.webSoft else TeaColors.webInk)
        if (locked) {
            Text(body, style = TeaTheme.type.small, color = TeaColors.webSoft)
        } else {
            VoiceLine(Persona.SPIDER, body)
        }
        if (note != null) {
            Text(note, style = TeaTheme.type.xs.copy(fontWeight = FontWeight.Bold), color = TeaColors.spiderSoft)
        }
    }
}

@Composable
private fun RecoveryCard(app: TeaApp, lessonId: String, s: AttacksSection, caught: Boolean) {
    val key = LearnKeys.recovery(lessonId)
    var ticks by remember { mutableStateOf(app.load(key, LearnKeys.INT_LIST).orEmpty().toSet()) }
    val r = s.recovery
    PersonaCard(
        Persona.TURTLE,
        Modifier.fillMaxWidth().padding(top = TeaSpacing.s2),
        title = if (caught) r.titleCaught else r.titleDefault,
        role = stringResource(Res.string.lesson_recovery_role),
        top = { VoiceLine(Persona.TURTLE, if (caught) r.leadCaught else r.leadDefault) },
        content = {
            r.steps.forEachIndexed { i, step ->
                TeaCheckbox(
                    checked = i in ticks,
                    onCheckedChange = { on ->
                        ticks = if (on) ticks + i else ticks - i
                        app.save(key, LearnKeys.INT_LIST, ticks.sorted())
                    },
                    label = step.headline,
                    supporting = step.body,
                )
            }
            VoiceLine(Persona.TURTLE, stringResource(Res.string.lesson_recovery_progress, ticks.size, r.steps.size), colored = true)
        },
    )
}

/** Finds a real vs look-alike domain pair on a scam item, for [DomainDiff]. */
object LookAlike {
    /** The host of a URL or the domain of an email address. */
    fun domainOf(s: String): String =
        s.substringAfter("://").substringAfterLast('@').substringBefore('/').substringBefore('?').lowercase()

    /**
     * (real, fake) when the item is a scam with a link question: fake = where the link really goes
     * (the right option), real = the most similar other option, if it is close enough to be a
     * look-alike. Null otherwise.
     */
    fun pair(item: Item): Pair<String, String>? {
        if (!item.isScam) return null
        val q = item.linkQuestion ?: return null
        val fake = q.options.getOrNull(q.correct)?.let(::domainOf) ?: return null
        val real = q.options.filterIndexed { i, _ -> i != q.correct }.map(::domainOf)
            .filter { it != fake }
            .minByOrNull { distance(it, fake) } ?: return null
        return if (distance(real, fake) <= max(3, fake.length / 2)) real to fake else null
    }

    /** Levenshtein distance. */
    fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
