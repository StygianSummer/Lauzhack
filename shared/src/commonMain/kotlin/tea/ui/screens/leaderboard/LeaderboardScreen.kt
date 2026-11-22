package tea.ui.screens.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.LeaderboardResponse
import tea.model.LeaderboardRow
import tea.net.ApiResult
import tea.ui.components.Banner
import tea.ui.components.DashedDivider
import tea.ui.components.OptionRow
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.screens.OkLine
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaTheme

private val PERIODS = listOf("week", "all")

/**
 * Leaderboard (plan §9.5): weekly / all-time XP, top 20 + you. Hidden when offline. "Invite a
 * friend" shares or copies the app link; the app never reads, uploads or stores contacts.
 */
@Composable
fun LeaderboardScreen(app: AppState) {
    val online by app.online.collectAsState()
    val loggedIn by app.loggedIn.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateMapOf<String, LeaderboardResponse>() }
    var failed by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf<Boolean?>(null) }
    val period = PERIODS[tab]

    LaunchedEffect(period, online) {
        if (online) {
            when (val r = app.leaderboard(period)) {
                is ApiResult.Ok -> { results[period] = r.value; failed = false }
                else -> failed = true
            }
        }
    }
    val inviteText = app.platform.appLink?.let { stringResource(Res.string.leaderboard_invite_text, it) }

    ScreenColumn(onBack = app::back) {
        SectionHeading(stringResource(Res.string.leaderboard_title), large = true, intro = stringResource(Res.string.leaderboard_intro))
        if (!online || failed) {
            Banner(stringResource(Res.string.leaderboard_offline))
        } else {
            OptionRow(
                options = listOf(stringResource(Res.string.leaderboard_week), stringResource(Res.string.leaderboard_all)),
                selected = tab,
                onSelect = { tab = it },
                stacked = false,
            )
            LeaderboardList(results[period], loggedIn)
            if (!loggedIn) {
                Banner(stringResource(Res.string.leaderboard_guest), actionText = stringResource(Res.string.settings_create_account), onAction = { app.navigate(Screen.Email) })
            }
        }
        if (inviteText != null) {
            TeaButton(
                stringResource(Res.string.leaderboard_invite),
                onClick = { shared = app.platform.shareOrCopy(inviteText) },
                variant = TeaButtonVariant.Secondary,
            )
            shared?.let { OkLine(stringResource(if (it) Res.string.leaderboard_shared else Res.string.leaderboard_copied)) }
        }
        Text(stringResource(Res.string.leaderboard_privacy), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
    }
}

@Composable
fun LeaderboardList(data: LeaderboardResponse?, loggedIn: Boolean) {
    TeaCard(Modifier.fillMaxWidth()) {
        if (data == null) {
            Text(stringResource(Res.string.about_loading), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
            return@TeaCard
        }
        if (data.top.isEmpty()) {
            Text(stringResource(Res.string.leaderboard_empty), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
        }
        val me = data.me
        // Only the row the server marks as ours: two learners can share a name and a rank.
        data.top.forEach { row -> RankRow(row, isMe = row.isMe) }
        if (loggedIn && me != null && data.top.none { it.isMe }) {
            DashedDivider()
            RankRow(me, isMe = true)
        }
    }
}

@Composable
private fun RankRow(row: LeaderboardRow, isMe: Boolean) {
    val colors = TeaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TeaShapes.sm)
            .background(if (isMe) colors.selectedSoft else colors.card)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.leaderboard_rank, row.rank), Modifier.width(36.dp), style = TeaTheme.type.bodyStrong, color = colors.inkSoft)
        Text(
            if (isMe) "${row.displayName} (${stringResource(Res.string.leaderboard_you)})" else row.displayName,
            Modifier.weight(1f),
            style = if (isMe) TeaTheme.type.bodyStrong else TeaTheme.type.body,
            color = colors.ink,
        )
        Text(stringResource(Res.string.leaderboard_xp, row.xp), style = TeaTheme.type.bodyStrong, color = colors.ink)
    }
}
