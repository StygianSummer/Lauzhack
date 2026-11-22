package tea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tea.BuildInfo
import tea.app.AppState
import tea.app.DeviceSnapshot
import tea.app.PlatformInfo
import tea.app.Routes
import tea.app.Screen
import tea.app.platformInfo
import tea.i18n.ProvideLanguage
import tea.ui.components.DebugFooter
import tea.ui.components.OfflineBanner
import tea.ui.screens.about.AboutScreen
import tea.ui.screens.gallery.GalleryScreen
import tea.ui.screens.home.HomeScreen
import tea.ui.screens.leaderboard.LeaderboardScreen
import tea.ui.screens.learn.FreePracticeScreen
import tea.ui.screens.learn.LessonScreen
import tea.ui.screens.learn.PlacementIntroScreen
import tea.ui.screens.learn.PlacementResultScreen
import tea.ui.screens.learn.PlacementScreen
import tea.ui.screens.learn.PracticeSummaryScreen
import tea.ui.screens.onboarding.CodeScreen
import tea.ui.screens.onboarding.EmailScreen
import tea.ui.screens.onboarding.LanguageScreen
import tea.ui.screens.onboarding.NameScreen
import tea.ui.screens.onboarding.OnboardingScreen
import tea.ui.screens.onboarding.PasswordScreen
import tea.ui.screens.onboarding.TermsScreen
import tea.ui.screens.reveal.RevealScreen
import tea.ui.screens.settings.SettingsScreen
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/** Entry point shared by the desktop and web shells (plan §9.1). */
@Composable
fun App(platform: PlatformInfo = remember { platformInfo() }) {
    val scope = rememberCoroutineScope()
    val app = remember { AppState.create(scope, platform) }
    AppRoot(app)
}

/**
 * Theme (dark / large text from the profile), language, router over [Screen], URL hash mirror on
 * web, offline banner and the [DebugFooter] (backend URL, online/offline, build sha) on every screen.
 */
@Composable
fun AppRoot(app: AppState) {
    val profile by app.profile.collectAsState()
    val lang by app.lang.collectAsState()
    val screen by app.screen.collectAsState()
    val online by app.online.collectAsState()
    val apiUrl by app.apiUrl.collectAsState()
    val dark = profile.darkMode ?: isSystemInDarkTheme()

    // Web: mirror the screen in location.hash (AppState follows back/forward and followed links).
    LaunchedEffect(screen) { app.platform.hash = Routes.toHash(screen) }

    ProvideLanguage(lang) {
        TeaTheme(dark = dark, largeText = profile.largeText) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val width = maxWidth.value.toInt()
                val fontScale = LocalDensity.current.fontScale
                LaunchedEffect(width, dark, fontScale) { app.updateDevice(DeviceSnapshot(dark, fontScale, width)) }
                if (screen is Screen.Gotcha) {
                    Router(app, screen)
                } else {
                    Column(Modifier.fillMaxSize().background(TeaTheme.colors.paper)) {
                        if (!online && screen != Screen.Gallery) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = TeaSpacing.s2, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                OfflineBanner(Modifier.widthIn(max = TeaSize.maxContentWidth), onRetry = app::retryConnection)
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) { Router(app, screen) }
                        DebugFooter(
                            apiUrl.removePrefix("https://").removePrefix("http://"),
                            if (online) "online" else "offline",
                            "build ${BuildInfo.SHA}",
                            modifier = Modifier.padding(horizontal = TeaSpacing.s2).padding(vertical = 0.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Router(app: AppState, screen: Screen) {
    when (screen) {
        Screen.Language -> LanguageScreen(app)
        Screen.Email -> EmailScreen(app)
        is Screen.Code -> CodeScreen(app, screen.email)
        Screen.Name -> NameScreen(app)
        Screen.Password -> PasswordScreen(app)
        is Screen.Onboarding -> OnboardingScreen(app, screen.q)
        Screen.Terms -> TermsScreen(app)
        Screen.PlacementIntro -> PlacementIntroScreen(app)
        Screen.Placement -> PlacementScreen(app)
        Screen.PlacementResult -> PlacementResultScreen(app)
        Screen.Home -> HomeScreen(app)
        is Screen.Lesson -> LessonScreen(app, screen.id, screen.section)
        is Screen.Gotcha -> GotchaScreen(app, screen)
        is Screen.Practice -> FreePracticeScreen(app, screen.focusWeakest)
        Screen.PracticeSummary -> PracticeSummaryScreen(app)
        is Screen.Reveal -> RevealScreen(app, screen.token)
        Screen.About -> AboutScreen(app)
        Screen.Settings -> SettingsScreen(app)
        Screen.Leaderboard -> LeaderboardScreen(app)
        Screen.Gallery -> GalleryScreen()
    }
}
