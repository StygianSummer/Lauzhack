package tea.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tea.model.BodyBlock
import tea.model.EmailContent
import tea.model.GotchaResponse
import tea.model.Hotspot
import tea.model.Mood
import tea.model.Persona
import tea.model.StepKey
import tea.ui.components.Banner
import tea.ui.components.ChoiceRow
import tea.ui.components.ChoiceState
import tea.ui.components.CodeField
import tea.ui.components.DebugFooter
import tea.ui.components.DomainDiff
import tea.ui.components.EmailDecoys
import tea.ui.components.EmailView
import tea.ui.components.FeedbackCard
import tea.ui.components.FeedbackKind
import tea.ui.components.OfflineBanner
import tea.ui.components.OptionRow
import tea.ui.components.PersonaBubble
import tea.ui.components.PersonaCard
import tea.ui.components.PersonaTitle
import tea.ui.components.SectionHeading
import tea.ui.components.SpiderPanel
import tea.ui.components.StepBar
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaCheckbox
import tea.ui.components.TeaDialog
import tea.ui.components.TeaDialogCard
import tea.ui.components.TeaDropdown
import tea.ui.components.TeaIconButton
import tea.ui.components.TeaIcons
import tea.ui.components.TeaProgressBar
import tea.ui.components.TeaTextField
import tea.ui.components.UrlTooltip
import tea.ui.components.VoiceLine
import tea.ui.screens.GotchaContent
import tea.ui.components.ScreenColumn
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import tea.ui.theme.personaStyle
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Developer gallery: every component, all four personas, with dark mode and large text toggles.
 * Dev-only screen, so its own labels are not localised.
 */
@Composable
fun GalleryScreen(
    initialDark: Boolean = isSystemInDarkTheme(),
    initialLargeText: Boolean = false,
    now: Instant = Clock.System.now(),
) {
    var dark by rememberSaveable { mutableStateOf(initialDark) }
    var large by rememberSaveable { mutableStateOf(initialLargeText) }
    TeaTheme(dark = dark, largeText = large) {
        ScreenColumn(
            title = "ThisIsTea design system",
            footer = { DebugFooter("gallery", if (dark) "dark" else "light", if (large) "large text" else "normal text") },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
                TeaCheckbox(dark, { dark = it }, "Dark mode")
                TeaCheckbox(large, { large = it }, "Large text")
            }
            GalleryContent(now)
        }
    }
}

/** The gallery body without theme or scaffold (reused by screenshot tests). */
@Composable
fun ColumnScope.GalleryContent(now: Instant = Clock.System.now()) {
    TypeSection()
    ColourSection()
    PersonaSection()
    ButtonSection()
    ChoiceSection()
    ProgressSection()
    FeedbackSection()
    InputSection()
    NoticeSection()
    SpiderSection()
    EmailSection(now)
    GotchaSection(now)
}

@Composable
private fun GotchaSection(now: Instant) {
    SectionHeading(
        "Gotcha interview",
        intro = "Recognition, reason choices, and both completed answer branches, shown without the reminder-email flow.",
    )
    val sample = GotchaResponse(
        sendId = "gallery-send",
        lessonId = "email-01",
        templateId = "gallery-sample",
        clickedAt = now,
        subject = "A quick check about your account",
        text = "Please review the practice page. This message is for the design gallery.",
        steps = mapOf(
            StepKey.WHO to "Check the real sender, not only the display name.",
            StepKey.EMOTION to "Notice whether the message is trying to rush you.",
            StepKey.ASK to "Pause and identify exactly what it asks you to do.",
            StepKey.VERIFY to "Check through a route you choose yourself.",
        ),
        hints = listOf("A familiar name is not proof of who sent a message."),
    )
    GotchaContent(
        data = sample,
        clickedWhen = "just now",
        answer = "yes",
        scrollEnabled = false,
        animateTransition = false,
        modifier = Modifier.fillMaxWidth(),
    )
    GotchaContent(
        data = sample,
        clickedWhen = "just now",
        answer = "no",
        scrollEnabled = false,
        animateTransition = false,
        modifier = Modifier.fillMaxWidth(),
    )
    GotchaContent(
        data = sample,
        clickedWhen = "just now",
        answer = "no",
        reason = "who",
        scrollEnabled = false,
        animateTransition = false,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TypeSection() {
    SectionHeading("Type", number = 1, intro = "Bricolage Grotesque for headings, Figtree for everything the app says.")
    val t = TeaTheme.type
    Text("Spot the trick", style = t.h1)
    Text("Section title", style = t.h2)
    Text("Card title", style = t.h3)
    Text("Question title", style = t.title)
    Text("Body text. Short sentences, second person, British spelling: favourite, organise.", style = t.body)
    Text("Small text for roles and captions.", style = t.small, color = TeaTheme.colors.inkSoft)
    Text("Extra small fine print.", style = t.xs, color = TeaTheme.colors.inkSoft)
    Text("thisistea.example/login", style = t.mono)
}

@Composable
private fun ColourSection() {
    SectionHeading("Colour", number = 2)
    val c = TeaTheme.colors
    val swatches = listOf(
        "paper" to c.paper, "card" to c.card, "ink" to c.ink, "inkSoft" to c.inkSoft, "rule" to c.rule,
        "highlight" to c.highlight, "ok" to c.ok, "bad" to c.bad, "link" to c.link, "focus" to c.focus,
    ) + Persona.entries.flatMap { p ->
        val s = personaStyle(p)
        listOf("${p.name.lowercase()}" to s.accent, "${p.name.lowercase()} surface" to s.surface)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        swatches.forEach { (name, color) -> Swatch(name, color) }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(Modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().size(height = 40.dp, width = 92.dp).clip(TeaShapes.sm).background(color).border(TeaSize.border, TeaTheme.colors.line, TeaShapes.sm))
        Text(name, style = TeaTheme.type.xs, color = TeaTheme.colors.inkSoft)
    }
}

@Composable
private fun PersonaSection() {
    SectionHeading("Personas", number = 3, intro = "A persona's font appears only on their name, their titles and what they say.")
    PersonaBubble(Persona.CAT, Mood.NEUTRAL, "Look at the sender's address. Not the name. The address.", role = "Step prompts")
    PersonaBubble(Persona.TURTLE, Mood.HAPPY, "That one was fine, dear. The link goes where it says it goes.", role = "Why it's fine")
    PersonaBubble(Persona.DOG, Mood.HAPPY, "Call the bank on the number on your card, buddy! Not the one in the email!", role = "What to do now")
    PersonaBubble(Persona.SPIDER, Mood.SMUG, "You almost clicked... I could tell.", role = "The trickster")
    PersonaBubble(Persona.TURTLE, Mood.NEUTRAL, "A bubble on the soft tint.", onSoft = true)

    val compact = TeaTheme.isCompact
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> PersonaCard(Persona.CAT, m, title = "You first", role = "Personal security", tilt = -1.5f) { VoiceLine(Persona.CAT, "Check the address. Every time.") } },
        { m -> PersonaCard(Persona.TURTLE, m, title = "Together", role = "Your organisation", tilt = 1.2f) { VoiceLine(Persona.TURTLE, "We look after each other, dear.") } },
        { m -> PersonaCard(Persona.DOG, m, title = "Free tools", role = "Helpers", tilt = 1f) { VoiceLine(Persona.DOG, "I know a tool for that!") } },
        { m -> PersonaCard(Persona.SPIDER, m, title = "Can you catch me?", role = "The attacker", tilt = -1.2f) { VoiceLine(Persona.SPIDER, "I hid three tricks...") } },
    )
    if (compact) {
        cards.forEach { it(Modifier.fillMaxWidth()) }
    } else {
        cards.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s3)) {
                pair.forEach { it(Modifier.weight(1f)) }
            }
        }
    }
    Persona.entries.forEach { PersonaTitle(it, "${it.name.lowercase().replaceFirstChar(Char::uppercase)} owns this title") }
}

@Composable
private fun ButtonSection() {
    SectionHeading("Buttons and cards", number = 4)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
        TeaButton("Start the lesson", {})
        TeaButton("Check my answers", {}, variant = TeaButtonVariant.Secondary)
        TeaButton("Skip", {}, variant = TeaButtonVariant.Text)
        TeaButton("Delete my account", {}, variant = TeaButtonVariant.Danger)
        TeaButton("Not yet", {}, enabled = false)
        TeaButton("Small", {}, variant = TeaButtonVariant.Secondary, small = true)
        TeaButton("Continue", {}, icon = TeaIcons.Check)
        TeaIconButton(TeaIcons.Back, "Back", {})
        TeaIconButton(TeaIcons.Cross, "Close", {})
    }
    TeaCard(Modifier.fillMaxWidth()) {
        Text("A card", style = TeaTheme.type.h3)
        Text("White, outlined, radius 22, hard shadow. No blur, no gradient.", style = TeaTheme.type.body)
    }
    TeaCard(Modifier.fillMaxWidth(), onClick = {}) {
        Text("A clickable card lifts on hover and sinks when pressed.", style = TeaTheme.type.body)
    }
}

@Composable
private fun ChoiceSection() {
    SectionHeading("Choices", number = 5)
    Text("Which address really belongs to the bank?", style = TeaTheme.type.title)
    ChoiceRow("Idle option", ChoiceState.Idle, {})
    ChoiceRow("Selected option", ChoiceState.Selected, {})
    ChoiceRow("Correct option", ChoiceState.Correct, {})
    ChoiceRow("Wrong option, struck out", ChoiceState.Wrong, {})
    ChoiceRow("Disabled option", ChoiceState.Disabled, {})
    ChoiceRow("https://alpine-savings.example/login", ChoiceState.Idle, {}, mono = true)
    ChoiceRow("Multi select, selected", ChoiceState.Selected, {}, multi = true)
    ChoiceRow("Multi select, correct", ChoiceState.Correct, {}, multi = true)
    ChoiceRow("Multi select, wrong", ChoiceState.Wrong, {}, multi = true)
    var sure by remember { mutableIntStateOf(1) }
    Text("How sure are you?", style = TeaTheme.type.title)
    OptionRow(listOf("Guessing", "Fairly sure", "Certain"), sure, { sure = it }, meters = listOf(0.33f, 0.66f, 1f))
}

@Composable
private fun ProgressSection() {
    SectionHeading("Progress", number = 6)
    TeaProgressBar(0.4f, Modifier.fillMaxWidth(), label = "Placement", trailingLabel = "6 of 16")
    StepBar(listOf("Who", "Emotion", "Ask", "Check", "Verdict"), current = 2)
}

@Composable
private fun FeedbackSection() {
    SectionHeading("Feedback", number = 7)
    FeedbackCard(FeedbackKind.Correct, Persona.TURTLE, "Well spotted, dear. The address gave it away.")
    FeedbackCard(FeedbackKind.NotQuite, Persona.SPIDER, "Not quite... look at the link once more.")
    FeedbackCard(FeedbackKind.Hint, Persona.CAT, "Hover over the link. Read where it really goes.")
}

@Composable
private fun InputSection() {
    SectionHeading("Inputs", number = 8)
    var email by remember { mutableStateOf("sam@") }
    var pw by remember { mutableStateOf("correct horse") }
    var code by remember { mutableStateOf("4821") }
    var terms by remember { mutableStateOf(true) }
    var country by remember { mutableStateOf<String?>("CH") }
    TeaTextField(email, { email = it }, "Email address", Modifier.fillMaxWidth(), placeholder = "you@example.org", error = "Please check the address.")
    TeaTextField("", {}, "First name", Modifier.fillMaxWidth(), placeholder = "Sam", helper = "We use it to greet you.")
    TeaTextField(pw, { pw = it }, "Password", Modifier.fillMaxWidth(), password = true, helper = "At least 10 characters, please.")
    CodeField(code, { code = it }, label = "Code from the email")
    TeaCheckbox(terms, { terms = it }, "I accept the terms of use", supporting = "You can read them first.")
    TeaDropdown("Country", listOf("CH", "FR", "ES", "DE", "IT", "GB", "Other"), country, { country = it }, { it }, Modifier.fillMaxWidth())
}

@Composable
private fun NoticeSection() {
    SectionHeading("Notices and dialogs", number = 9)
    OfflineBanner(onRetry = {})
    Banner("Your progress is saved on this device.")
    var open by remember { mutableStateOf(false) }
    TeaButton("Open a dialog", { open = true }, variant = TeaButtonVariant.Secondary)
    TeaDialogCard(
        "Delete my account?",
        {},
        confirmText = "Delete",
        dismissText = "Keep it",
        destructive = true,
    ) {
        Text("This removes everything we store about you. It cannot be undone.", style = TeaTheme.type.body)
    }
    if (open) {
        TeaDialog({ open = false }, "A real dialog", confirmText = "OK", onConfirm = { open = false }, dismissText = "Cancel") {
            Text("Esc or a click outside closes it.", style = TeaTheme.type.body)
        }
    }
}

@Composable
private fun SpiderSection() {
    SectionHeading("Spider panel", number = 10)
    SpiderPanel {
        SectionHeading("How I fooled you", number = 3)
        PersonaBubble(Persona.SPIDER, Mood.SMUG, "One letter. That's all it took...")
        DomainDiff("thisistea.example", "thisistae-app.example")
    }
}

/** Sample email (fictional bank, `.example` domains). */
val GallerySampleEmail = EmailContent(
    fromName = "Alpine Savings",
    fromAddress = "support@alpine-savings-secure.example",
    replyTo = "help.desk@mailbox-free.example",
    to = "sam@thisistea.example",
    subject = "Action required: confirm your account within 2 hours",
    received = "-PT2H",
    body = listOf(
        BodyBlock("text", "Dear customer,"),
        BodyBlock("text", "We noticed an unusual sign-in. Please confirm your details within 2 hours or your account will be locked.", hotspot = "urgency"),
        BodyBlock("link", "Confirm my account", href = "https://alpine-savings.secure-login.example/confirm"),
        BodyBlock("signature", "Alpine Savings\nCustomer Care"),
    ),
    attachments = listOf("statement.pdf"),
)

val GallerySampleHotspots = listOf(
    Hotspot("sender", "from_address", StepKey.WHO, redFlag = true, explain = "The domain is not the bank's."),
    Hotspot("urgency", "body:1", StepKey.EMOTION, redFlag = true, explain = "A deadline to rush you."),
    Hotspot("link", "link", StepKey.ASK, redFlag = true, explain = "The link goes elsewhere."),
)

@Composable
private fun EmailSection(now: Instant) {
    SectionHeading("Email", number = 11, intro = "Tap what looks wrong. Hover or long-press a link to see where it really goes.")
    var found by remember { mutableStateOf(setOf("sender", EmailDecoys.GREETING)) }
    EmailView(
        GallerySampleEmail,
        GallerySampleHotspots,
        tappable = true,
        highlighted = found,
        onTap = { id -> found = if (id in found) found - id else found + id },
        decoys = true,
        now = now,
    )
    Text("Link tooltip:", style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
    UrlTooltip("https://alpine-savings.secure-login.example/confirm")
}
