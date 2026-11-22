package tea.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** `--s-1` … `--s-5`. */
object TeaSpacing {
    val s1 = 8.dp
    val s2 = 16.dp
    val s3 = 24.dp
    val s4 = 36.dp
    val s5 = 56.dp
}

/** Radii, borders, shadows and layout sizes (plan §8.1, §9.1). */
object TeaSize {
    val radiusLg = 22.dp
    val radiusMd = 14.dp
    val radiusSm = 10.dp
    val radiusPanel = 28.dp

    /** `--border`: 2dp solid line on every card, button, avatar, bubble. */
    val border = 2.dp

    /** `--stick`. */
    val shadow = 4.dp
    /** `--stick-sm`. */
    val shadowSm = 3.dp
    /** Attack panel and hover. */
    val shadowLg = 6.dp

    val focusRing = 3.dp
    val focusOffset = 3.dp

    val minTouch = 48.dp
    val maxContentWidth = 720.dp
    /** Below this width the layout is "compact" (single column, smaller headings). */
    val compactBreakpoint = 600.dp
    val avatar = 48.dp
    val avatarSm = 38.dp
}

object TeaShapes {
    val lg = RoundedCornerShape(TeaSize.radiusLg)
    val md = RoundedCornerShape(TeaSize.radiusMd)
    val sm = RoundedCornerShape(TeaSize.radiusSm)
    val panel = RoundedCornerShape(TeaSize.radiusPanel)
    val pill = RoundedCornerShape(percent = 50)
    val circle = CircleShape
    /** Hunt regions and inline marks. */
    val mark = RoundedCornerShape(4.dp)
}
