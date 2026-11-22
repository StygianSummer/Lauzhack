package tea.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.TERMS_VERSION
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaCheckbox
import tea.ui.components.TeaIcons
import tea.ui.screens.BulletList
import tea.ui.screens.goNext
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * Terms (plan §9.2.7): required checkbox, always-visible summary of what we store, the full terms
 * behind "Read the terms" (with the clearly titled "Practice emails" section). Accepting emits
 * `terms_accepted` and does not switch practice emails on.
 */
@Composable
fun TermsScreen(app: AppState) {
    var accepted by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    ScreenColumn(onBack = backOrNull(app)) {
        SectionHeading(stringResource(Res.string.terms_title), large = true, intro = stringResource(Res.string.terms_intro))
        TeaCard {
            Text(stringResource(Res.string.terms_summary_title), style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
            BulletList(
                listOf(
                    stringResource(Res.string.terms_summary_1),
                    stringResource(Res.string.terms_summary_2),
                    stringResource(Res.string.terms_summary_3),
                    stringResource(Res.string.terms_summary_4),
                    stringResource(Res.string.terms_summary_5),
                ),
            )
            TeaButton(
                stringResource(if (expanded) Res.string.terms_hide else Res.string.terms_read),
                onClick = { expanded = !expanded },
                variant = TeaButtonVariant.Text,
                small = true,
                icon = TeaIcons.ChevronDown,
            )
        }
        if (expanded) TermsText(Modifier.fillMaxWidth())
        TeaCheckbox(accepted, { accepted = it }, stringResource(Res.string.terms_accept))
        TeaButton(
            stringResource(Res.string.terms_start),
            onClick = {
                app.acceptTerms()
                app.goNext(if (app.profile.value.placement == null) Screen.PlacementIntro else Screen.Home)
            },
            enabled = accepted,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The full terms in a bounded scrolling box. */
@Composable
fun TermsText(modifier: Modifier = Modifier, maxHeight: Int = 380) {
    val colors = TeaTheme.colors
    Column(
        modifier
            .heightIn(max = maxHeight.dp)
            .clip(TeaShapes.md)
            .background(colors.card)
            .border(TeaSize.border, colors.line, TeaShapes.md)
            .verticalScroll(rememberScrollState())
            .padding(TeaSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1),
    ) {
        Text(stringResource(Res.string.terms_version, TERMS_VERSION), style = TeaTheme.type.xs, color = colors.inkSoft)
        Text(stringResource(Res.string.terms_body), style = TeaTheme.type.body, color = colors.ink)
        Section(stringResource(Res.string.terms_data_title), stringResource(Res.string.terms_data_body))
        Section(stringResource(Res.string.terms_practice_title), stringResource(Res.string.terms_practice_body))
        Section(stringResource(Res.string.terms_use_title), stringResource(Res.string.terms_use_body))
        Section(stringResource(Res.string.terms_rights_title), stringResource(Res.string.terms_rights_body))
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(title, Modifier.padding(top = 8.dp).semantics { heading() }, style = TeaTheme.type.title, color = TeaTheme.colors.ink)
    Text(body, style = TeaTheme.type.body, color = TeaTheme.colors.ink)
}
