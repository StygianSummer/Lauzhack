package tea.learn

import androidx.compose.runtime.Composable
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import tea.app.FakeTeaApp
import tea.engine.ChallengeBest
import tea.engine.ChallengeOutcome
import tea.model.BOARDROOM_CHALLENGE_ID
import tea.model.Challenge
import tea.model.LeaderboardResponse
import tea.model.LeaderboardRow
import tea.ui.Screenshots
import tea.ui.components.ScreenColumn
import tea.ui.screens.challenge.ChallengeBody
import tea.ui.screens.challenge.ChallengeController
import tea.ui.screens.challenge.DecisionCard
import tea.ui.screens.home.ChapterProgress
import tea.ui.screens.home.ChapterUi
import tea.ui.screens.home.HomeActions
import tea.ui.screens.home.HomeContent
import tea.ui.screens.home.HomeUi
import tea.ui.screens.home.RankingUi
import tea.ui.theme.TeaTheme

/**
 * The chapter Home and every section of the Boardroom crisis, rendered to
 * `shared/build/screenshots/challenge_*.png` (looked at, not compared).
 */
class ChallengeScreenshotsTest {
    private val repo = TestContent.repository()
    private fun challenge(lang: String = "en"): Challenge = runBlocking { repo.challenge(BOARDROOM_CHALLENGE_ID, lang)!! }
    private fun app() = FakeTeaApp(repo, xp = 140)

    private fun shot(name: String, dark: Boolean = false, width: Int = 760, height: Int = 2600, content: @Composable () -> Unit) {
        val files = Screenshots.render("challenge_$name", widthDp = width, heightDp = height, density = 1f, content = {
            TeaTheme(dark = dark) { content() }
        })
        assertTrue(files.all { it.length() > 0 })
    }

    private fun controller(lang: String = "en", vararg picks: List<Int>): ChallengeController {
        val c = ChallengeController(app(), challenge(lang)) { 3L }
        picks.forEachIndexed { i, sel -> c.lock(i, sel) }
        return c
    }

    private val perfect = arrayOf(listOf(0), listOf(0, 1, 2), listOf(0), listOf(0), listOf(0, 1, 2))
    private val mixed = arrayOf(listOf(1), listOf(1), listOf(0), listOf(2), listOf(0, 1, 2))

    // ---- sections ----

    @Test fun briefing() {
        shot("briefing", height = 3600) { ChallengeBody(controller(), 0) {} }
        shot("briefing_compact", width = 400, height = 5200) { ChallengeBody(controller(), 0) {} }
        shot("briefing_dark", dark = true, height = 3600) { ChallengeBody(controller(), 0) {} }
        shot("briefing_fr", height = 3600) { ChallengeBody(controller("fr"), 0) {} }
    }

    @Test fun playbook() {
        shot("playbook", height = 1600) { ChallengeBody(controller(), 1) {} }
        shot("playbook_compact", width = 400, height = 2600) { ChallengeBody(controller(), 1) {} }
    }

    @Test fun decisionOpen() {
        shot("decision_open", height = 1800) { ChallengeBody(controller(), 2) {} }
        shot("decision_open_compact", width = 400, height = 2400) { ChallengeBody(controller(), 2) {} }
        shot("decision_open_dark", dark = true, height = 1800) { ChallengeBody(controller(), 2) {} }
    }

    @Test fun decisionLocked() {
        val sound = controller("en", listOf(0))
        shot("decision_locked_sound", height = 2000) { ScreenColumn { DecisionCard(sound, 0, onReview = {}, onNext = {}) } }
        val costly = controller("en", listOf(0), listOf(1, 3))
        shot("decision_locked_costly_multi", height = 3000) { ScreenColumn { DecisionCard(costly, 1, onReview = {}, onNext = {}) } }
        shot("decision_locked_costly_multi_compact", width = 400, height = 4200) { ScreenColumn { DecisionCard(costly, 1, onReview = {}, onNext = {}) } }
        shot("decision_second_with_earlier", height = 2200) { ChallengeBody(costly, 2) {} }
    }

    @Test fun fallout() {
        shot("fallout_wait", height = 900) { ChallengeBody(controller(), 3) {} }
        shot("fallout_contained", height = 3400) { ChallengeBody(controller("en", *perfect), 3) {} }
        shot("fallout_wrecked", height = 3800) { ChallengeBody(controller("en", *mixed), 3) {} }
        shot("fallout_wrecked_dark", dark = true, height = 3800) { ChallengeBody(controller("en", *mixed), 3) {} }
        shot("fallout_es_compact", width = 400, height = 5200) { ChallengeBody(controller("es", *mixed), 3) {} }
    }

    // ---- Home ----

    private fun homeUi(lang: String = "en", ranking: RankingUi = rankingReady, placement: Boolean = true, loggedIn: Boolean = true): HomeUi {
        val chapters = runBlocking { repo.chapters(lang) }
        val progress = listOf(
            ChapterProgress.LessonSection(2, 6),
            ChapterProgress.NotStarted,
            ChapterProgress.ChallengeDone(ChallengeBest(4, 5, ChallengeOutcome.BRUISED)),
            ChapterProgress.NotStarted,
        )
        return HomeUi(
            firstName = "Sam", xp = 340, streak = 3, dailyMinutes = 5,
            chapters = chapters.mapIndexed { i, c -> ChapterUi(c, progress.getOrElse(i) { ChapterProgress.NotStarted }) },
            showPlacement = placement, ranking = ranking, loggedIn = loggedIn,
        )
    }

    private val rankingReady = RankingUi.Ready(
        LeaderboardResponse(
            top = listOf(LeaderboardRow("Lea", 520, 1), LeaderboardRow("Noor", 480, 2), LeaderboardRow("Jo", 410, 3)),
            me = LeaderboardRow("Sam", 340, 7, isMe = true),
        ),
    )

    @Test fun home() {
        shot("home_wide", width = 1280, height = 1400) { HomeContent(homeUi(), HomeActions()) }
        shot("home_wide_dark", dark = true, width = 1280, height = 1400) { HomeContent(homeUi(), HomeActions()) }
        shot("home_medium", width = 800, height = 2000) { HomeContent(homeUi(placement = false), HomeActions()) }
        shot("home_level_basics", width = 800, height = 2200) { HomeContent(homeUi(placement = false).copy(placementLevel = 3), HomeActions()) }
        shot("home_level_start_compact", width = 380, height = 2600) { HomeContent(homeUi(placement = false).copy(placementLevel = 1), HomeActions()) }
        shot("home_compact_guest", width = 400, height = 3000) {
            HomeContent(homeUi(ranking = RankingUi.Guest, loggedIn = false), HomeActions())
        }
        shot("home_compact_fr", width = 400, height = 3000) { HomeContent(homeUi("fr", ranking = RankingUi.Offline), HomeActions()) }
    }
}
