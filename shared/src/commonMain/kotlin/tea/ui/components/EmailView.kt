package tea.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.generated.resources.Res
import tea.generated.resources.c_days_ago
import tea.generated.resources.c_from
import tea.generated.resources.c_hours_ago
import tea.generated.resources.c_inbox
import tea.generated.resources.c_just_now
import tea.generated.resources.c_minutes_ago
import tea.generated.resources.c_reply_to
import tea.generated.resources.c_subject
import tea.generated.resources.c_to
import tea.model.BodyBlock
import tea.model.EmailContent
import tea.model.Hotspot
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.focusRing
import tea.ui.theme.hardShadow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A generic mail client showing [email] (no real-brand look): grey title bar, header rows
 * (From name + address, Reply-To only if set, To, Subject, received time), body blocks and
 * attachment chips.
 *
 * Links are underlined blue and never navigate; hovering (desktop/web) or long-pressing (touch)
 * shows the real `href` in a small monospace tooltip.
 *
 * When [tappable], every hotspot target is a tappable region (and with [decoys], the greeting and
 * the signature, ids [EmailDecoys.GREETING] / [EmailDecoys.SIGNATURE]); taps call [onTap] with the
 * region id (hotspot id or decoy id). Regions whose id is in [highlighted] get the highlight
 * background and a Spider-coloured outline; the caller owns that set (add the tapped id to it).
 * Highlighting also works when not [tappable] (e.g. to show where the flags were).
 *
 * @param now reference time for the relative "received" label (`email.received` is a duration ago).
 */
@Composable
fun EmailView(
    email: EmailContent,
    hotspots: List<Hotspot>,
    tappable: Boolean,
    highlighted: Set<String>,
    onTap: (regionId: String) -> Unit,
    modifier: Modifier = Modifier,
    decoys: Boolean = false,
    now: Instant = Clock.System.now(),
) {
    val colors = TeaTheme.colors
    val type = TeaTheme.type
    val regions = remember(email, hotspots, decoys) { emailRegions(email, hotspots, decoys && tappable) }
    val shape = TeaShapes.md
    val ctx = RegionContext(tappable, highlighted, onTap)

    Column(
        modifier
            .fillMaxWidth()
            .hardShadow(colors.shadow, shape)
            .clip(shape)
            .background(colors.card)
            .border(TeaSize.border, colors.line, shape),
    ) {
        // Title bar
        Row(
            Modifier.fillMaxWidth().background(colors.chrome).bottomRule(colors.line, TeaSize.border)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { Box(Modifier.size(11.dp).clip(TeaShapes.circle).background(colors.chromeDot)) }
            Text(stringResource(Res.string.c_inbox), Modifier.padding(start = 6.dp), style = type.smallStrong, color = colors.inkSoft)
            Box(Modifier.weight(1f))
            Text(receivedLabel(email.received, now), style = type.xs, color = colors.inkSoft)
        }

        // Header
        Column(
            Modifier.fillMaxWidth().bottomRule(colors.rule, 1.dp).padding(start = 19.dp, end = 19.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HeaderRow(stringResource(Res.string.c_from)) {
                RegionText(email.fromName, regions[EmailPart.FromName], ctx, type.small.copy(fontWeight = FontWeight.Bold))
                RegionText("<${email.fromAddress}>", regions[EmailPart.FromAddress], ctx, type.small)
            }
            email.replyTo?.let { replyTo ->
                HeaderRow(stringResource(Res.string.c_reply_to)) {
                    RegionText(replyTo, regions[EmailPart.ReplyTo], ctx, type.small)
                }
            }
            HeaderRow(stringResource(Res.string.c_to)) {
                RegionText(email.to, null, ctx, type.small)
            }
            HeaderRow(stringResource(Res.string.c_subject)) {
                RegionText(email.subject, regions[EmailPart.Subject], ctx, type.small.copy(fontWeight = FontWeight.Bold))
            }
        }

        // Body
        Column(
            Modifier.fillMaxWidth().padding(19.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            email.body.forEachIndexed { i, block ->
                val id = regions[EmailPart.Body(i)]
                when (block.t) {
                    "link" -> LinkBlock(block, id, ctx)
                    "signature" -> RegionText(block.v, id, ctx, type.body, colors.inkSoft)
                    else -> RegionText(block.v, id, ctx, type.body)
                }
            }
            if (email.attachments.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    email.attachments.forEachIndexed { i, name ->
                        AttachmentChip(name, regions[EmailPart.Attachment(i)], ctx)
                    }
                }
            }
        }
    }
}

private class RegionContext(
    val tappable: Boolean,
    val highlighted: Set<String>,
    val onTap: (String) -> Unit,
)

private fun Modifier.bottomRule(color: Color, width: androidx.compose.ui.unit.Dp) = drawBehind {
    val w = width.toPx()
    drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

@Composable
private fun HeaderRow(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            Modifier.widthIn(min = 76.dp).padding(top = 1.dp, end = 8.dp),
            style = TeaTheme.type.small.copy(fontWeight = FontWeight.SemiBold),
            color = TeaTheme.colors.inkSoft,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            value()
        }
    }
}

/**
 * Wraps a region: tappable (button semantics, focus ring, hover tint) when [id] is set and the
 * view is tappable; highlight background + spider outline when [id] is highlighted.
 */
@Composable
private fun Modifier.region(id: String?, ctx: RegionContext): Modifier {
    if (id == null) return this
    val colors = TeaTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val isHi = id in ctx.highlighted
    val shape = TeaShapes.mark
    var m = this.focusRing(source, shape, colors.focus).clip(shape)
    m = when {
        isHi -> m.background(colors.highlight).border(TeaSize.border, TeaColors.spider, shape)
        ctx.tappable && hovered -> m.background(colors.highlightHover)
        else -> m
    }
    if (ctx.tappable) {
        m = m.clickable(source, indication = null, role = Role.Button) { ctx.onTap(id) }
            .semantics { selected = isHi }
    }
    return m.padding(horizontal = 4.dp, vertical = 1.dp)
}

@Composable
private fun RegionText(text: String, id: String?, ctx: RegionContext, style: TextStyle, color: Color = TeaTheme.colors.ink) {
    val isHi = id != null && id in ctx.highlighted
    Text(
        text,
        Modifier.region(id, ctx),
        style = style,
        color = if (isHi) TeaTheme.colors.onHighlight else color,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkBlock(block: BodyBlock, id: String?, ctx: RegionContext) {
    val colors = TeaTheme.colors
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var pinned by remember { mutableStateOf(false) }
    val isHi = id != null && id in ctx.highlighted
    val href = block.href ?: block.v
    val clickTarget = id?.takeIf { ctx.tappable }
    val shape = TeaShapes.mark
    val source = remember { MutableInteractionSource() }

    LaunchedEffect(pinned) {
        if (pinned) {
            delay(4000)
            pinned = false
        }
    }

    Box {
        Text(
            block.v,
            Modifier
                .hoverable(hover)
                .focusRing(source, shape, colors.focus)
                .clip(shape)
                .then(if (isHi) Modifier.background(colors.highlight).border(TeaSize.border, TeaColors.spider, shape) else Modifier)
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    role = if (clickTarget != null) Role.Button else null,
                    onLongClick = { pinned = true },
                    onClick = { clickTarget?.let(ctx.onTap) },
                )
                .then(if (clickTarget == null) Modifier.focusProperties { canFocus = false } else Modifier)
                .padding(horizontal = 4.dp, vertical = 1.dp),
            style = TeaTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (isHi) colors.onHighlight else colors.link,
            textDecoration = TextDecoration.Underline,
        )
        if (hovered || pinned) {
            Popup(
                popupPositionProvider = remember { TooltipBelow() },
                onDismissRequest = { pinned = false },
                properties = PopupProperties(focusable = false),
            ) {
                UrlTooltip(href)
            }
        }
    }
}

/** The small monospace tooltip showing a link's real target (ink on paper inverted). */
@Composable
fun UrlTooltip(href: String, modifier: Modifier = Modifier) {
    val colors = TeaTheme.colors
    Text(
        href,
        modifier
            .widthIn(max = 420.dp)
            .clip(TeaShapes.sm)
            .background(colors.ink)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = TeaTheme.type.monoSmall,
        color = colors.paper,
    )
}

private class TooltipBelow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 6
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun AttachmentChip(name: String, id: String?, ctx: RegionContext) {
    val colors = TeaTheme.colors
    val isHi = id != null && id in ctx.highlighted
    val fg = if (isHi) colors.onHighlight else colors.ink
    Row(
        Modifier
            .clip(TeaShapes.pill)
            .border(TeaSize.border, colors.rule, TeaShapes.pill)
            .region(id, ctx)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TeaIcon(TeaIcons.Document, null, tint = fg, size = 16.dp)
        Text(name, style = TeaTheme.type.small, color = fg)
    }
}

/** "14:05 · 2 hours ago" from an ISO-8601 duration like "-PT2H" relative to [now]. */
@Composable
private fun receivedLabel(received: String, now: Instant): String {
    val d = Duration.parseIsoStringOrNull(received) ?: Duration.ZERO
    val ago = d.absoluteValue
    val minutes = ago.inWholeMinutes.toInt()
    val hours = ago.inWholeHours.toInt()
    val days = ago.inWholeDays.toInt()
    val relative = when {
        minutes < 1 -> stringResource(Res.string.c_just_now)
        minutes < 60 -> pluralStringResource(Res.plurals.c_minutes_ago, minutes, minutes)
        hours < 24 -> pluralStringResource(Res.plurals.c_hours_ago, hours, hours)
        else -> pluralStringResource(Res.plurals.c_days_ago, days, days)
    }
    if (days >= 1) return relative
    val at = (now - ago).toLocalDateTime(TimeZone.currentSystemDefault())
    val clock = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    return "$clock · $relative"
}
