package tea.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Raw tokens from `static/tea-theme.css` (plan §8.1). Components should not read these directly:
 * use [TeaTheme.colors] (narrator, follows light/dark) or [personaStyle] (persona colours).
 * The only components allowed to use the `web*` tokens directly are the Spider-only ones
 * (SpiderPanel, DomainDiff, Spider bubbles), because Spider is always dark.
 */
object TeaColors {
    // Narrator, light
    val paper = Color(0xFFEEF2F9)
    val card = Color(0xFFFFFFFF)
    val ink = Color(0xFF111B3A)
    val inkSoft = Color(0xFF4B5775)
    val line = Color(0xFF111B3A)
    val rule = Color(0xFFC7D0E4)
    val highlight = Color(0xFFFFE66D)

    // Personas
    val cat = Color(0xFFF5A524); val catDeep = Color(0xFF9A5B00); val catSoft = Color(0xFFFFF1D6)
    val turtle = Color(0xFF1FA97A); val turtleDeep = Color(0xFF0C6B4B); val turtleSoft = Color(0xFFD9F5EA)
    val dog = Color(0xFF3B82F6); val dogDeep = Color(0xFF1D4FB8); val dogSoft = Color(0xFFDBE8FF)
    val spider = Color(0xFFE5384F); val spiderDeep = Color(0xFFB0182E); val spiderSoft = Color(0xFFFFE0E5)

    // Feedback
    val ok = turtle
    val okDeep = turtleDeep
    val bad = spider
    val badDeep = Color(0xFF7A1424)

    // Spider's night palette (also the base for dark mode)
    val webBg = Color(0xFF1B1026)
    val webCard = Color(0xFF2A1A38)
    val webLine = Color(0xFF4A3560)
    val webInk = Color(0xFFF6E9FF)
    val webSoft = Color(0xFFB9A3CC)
    val gotchaCard = Color(0xFF4A1425)

    /** `.domain-row.real` on the night palette. */
    val webOk = Color(0xFF9CF0CD)

    /** Text on the primary (turtle) button, `.btn-go`. */
    val onGo = Color(0xFF04261A)
    /** `.btn-go:hover`. */
    val goHover = Color(0xFF2DC08E)
}

/**
 * Narrator palette, resolved for light or dark mode. Read it with `TeaTheme.colors`.
 * Semantic slots (ok*, bad*, selected*, link, focus) are already adjusted for contrast on the
 * current surfaces, so components never branch on dark mode themselves.
 */
@Immutable
data class NarratorColors(
    val isDark: Boolean,
    val paper: Color,
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val rule: Color,
    /** Hard shadow colour (ink in light mode, black in dark mode). */
    val shadow: Color,
    /** "Look here" marker. Text on it uses [onHighlight]. */
    val highlight: Color,
    val onHighlight: Color,
    /** Hover tint for hunt regions (`#fff6bf`). */
    val highlightHover: Color,
    /** Fills and borders for "correct" (turtle family). */
    val ok: Color,
    /** Text meaning "correct" on [card]/[paper]. */
    val okText: Color,
    /** Background of a correct row. */
    val okSoft: Color,
    val bad: Color,
    val badText: Color,
    val badSoft: Color,
    /** Hint (cat family) text. */
    val hintText: Color,
    /** Background of a selected, not yet graded row (dog-soft). */
    val selectedSoft: Color,
    /** Focus ring (dog). */
    val focus: Color,
    /** Fake link text in mock emails. */
    val link: Color,
    /** Unfilled progress / step bar. */
    val track: Color,
    /** Grey title bar of the mock mail client. */
    val chrome: Color,
    val chromeDot: Color,
    /** Primary (go) button. */
    val go: Color,
    val goHover: Color,
    val onGo: Color,
)

val LightNarrator = NarratorColors(
    isDark = false,
    paper = TeaColors.paper,
    card = TeaColors.card,
    ink = TeaColors.ink,
    inkSoft = TeaColors.inkSoft,
    line = TeaColors.line,
    rule = TeaColors.rule,
    shadow = TeaColors.ink,
    highlight = TeaColors.highlight,
    onHighlight = Color(0xFF000000),
    highlightHover = Color(0xFFFFF6BF),
    ok = TeaColors.ok,
    okText = TeaColors.okDeep,
    okSoft = TeaColors.turtleSoft,
    bad = TeaColors.bad,
    badText = TeaColors.badDeep,
    badSoft = TeaColors.spiderSoft,
    hintText = TeaColors.catDeep,
    selectedSoft = TeaColors.dogSoft,
    focus = TeaColors.dog,
    link = Color(0xFF2563EB),
    track = Color(0xFFCDD6E8),
    chrome = Color(0xFFE7EBF4),
    chromeDot = Color(0xFFAAB4CC),
    go = TeaColors.turtle,
    goHover = TeaColors.goHover,
    onGo = TeaColors.onGo,
)

/** Dark narrator (plan §8.1): neo-brutal kept, light outlines, black hard shadows. */
val DarkNarrator = NarratorColors(
    isDark = true,
    paper = Color(0xFF10172E),
    card = Color(0xFF1A2342),
    ink = Color(0xFFEEF2F9),
    inkSoft = Color(0xFFB4BFD9),
    line = Color(0xFFEEF2F9),
    rule = Color(0xFF3A4670),
    shadow = Color(0xFF000000),
    highlight = TeaColors.highlight,
    onHighlight = Color(0xFF000000),
    highlightHover = Color(0xFF4A4420),
    ok = TeaColors.ok,
    okText = Color(0xFF5FD9AE),
    okSoft = Color(0xFF0F3A2B),
    bad = TeaColors.bad,
    badText = Color(0xFFFF8A99),
    badSoft = Color(0xFF4A1425),
    hintText = Color(0xFFFFC766),
    selectedSoft = Color(0xFF1E3366),
    focus = TeaColors.dog,
    link = Color(0xFF8AB4FF),
    track = Color(0xFF2E3A60),
    chrome = Color(0xFF232D52),
    chromeDot = Color(0xFF55618A),
    go = TeaColors.turtle,
    goHover = TeaColors.goHover,
    onGo = TeaColors.onGo,
)
