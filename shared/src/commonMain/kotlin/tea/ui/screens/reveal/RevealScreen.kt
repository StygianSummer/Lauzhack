package tea.ui.screens.reveal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.Mood
import tea.model.Persona
import tea.model.RevealResponse
import tea.net.ApiResult
import tea.ui.components.Banner
import tea.ui.components.OfflineBanner
import tea.ui.components.PersonaBubble
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.SpiderPanel
import tea.ui.components.TeaButton
import tea.ui.components.TeaCard
import tea.ui.screens.BulletList
import tea.ui.screens.FourStepsList

/**
 * Reveal (plan §9.7), reached from the practice email's link (`#/reveal/<token>`), signed in or not.
 * Short and blame-free; the four steps and hints come from the field template on the server.
 */
@Composable
fun RevealScreen(app: AppState, token: String) {
    val online by app.online.collectAsState()
    var data by remember(token) { mutableStateOf<RevealResponse?>(null) }
    var notFound by remember(token) { mutableStateOf(false) }
    var round by remember { mutableStateOf(0) }

    LaunchedEffect(token, online, round) {
        if (data == null && online) {
            when (val r = app.reveal(token)) {
                is ApiResult.Ok -> data = r.value
                is ApiResult.Err -> notFound = r.status == 404
                ApiResult.Offline -> Unit
            }
        }
    }
    val stack by app.stack.collectAsState()
    RevealContent(
        data = data,
        notFound = notFound,
        online = online,
        onPractise = { app.navigate(Screen.Practice(focusWeakest = true)) },
        onRetry = { app.retryConnection(); round++ },
        onBack = if (stack.size > 1) app::back else null,
    )
}

@Composable
fun RevealContent(
    data: RevealResponse?,
    notFound: Boolean,
    online: Boolean,
    onPractise: () -> Unit,
    onRetry: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    ScreenColumn(onBack = onBack) {
        SpiderPanel {
            // Unknown token: do not claim "you clicked our email" (honesty rule, plan §17).
            SectionHeading(stringResource(Res.string.reveal_title), large = true, intro = if (notFound) null else stringResource(Res.string.reveal_lead))
            if (!notFound) PersonaBubble(Persona.SPIDER, Mood.SMUG, stringResource(Res.string.reveal_spider))
        }
        when {
            notFound -> Banner(stringResource(Res.string.reveal_not_found))
            data == null && !online -> OfflineBanner(onRetry = onRetry)
            data != null -> {
                TeaCard(Modifier.fillMaxWidth()) {
                    SectionHeading(stringResource(Res.string.reveal_steps_title))
                    FourStepsList(data.steps)
                }
                if (data.hints.isNotEmpty()) {
                    TeaCard(Modifier.fillMaxWidth()) {
                        SectionHeading(stringResource(Res.string.reveal_hints_title))
                        BulletList(data.hints)
                    }
                }
            }
            else -> Text(stringResource(Res.string.about_loading))
        }
        TeaButton(stringResource(Res.string.reveal_practise), onPractise, Modifier.fillMaxWidth())
    }
}
