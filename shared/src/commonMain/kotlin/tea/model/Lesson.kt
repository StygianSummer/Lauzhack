package tea.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Contract C3: `content/lessons/<id>/lesson.<lang>.json` (plan §4.2). */
@Serializable
data class Lesson(
    val id: String,
    val language: String,
    val title: String,
    val subtitle: String = "",
    val minutes: Int = 10,
    val sections: List<LessonSection>,
)

/** Polymorphic on the JSON field "type": content | tools | attacks | status | practice | readiness. */
@Serializable
sealed interface LessonSection

@Serializable
@SerialName("content")
data class ContentSection(val cards: List<RuleCard>) : LessonSection

@Serializable
data class RuleCard(
    val persona: Persona,
    val role: String,
    val title: String,
    val say: String,
    val rules: List<Rule>,
    val signOff: String = "",
)

@Serializable
data class Rule(val headline: String, val body: String)

@Serializable
@SerialName("tools")
data class ToolsSection(val intro: String, val tools: List<ToolRow>) : LessonSection

/** The url comes from resources.json by resourceId. */
@Serializable
data class ToolRow(
    val resourceId: String,
    val name: String,
    val what: String,
    val dogSays: String,
    val button: String,
)

@Serializable
@SerialName("attacks")
data class AttacksSection(val hunt: Hunt, val recovery: Recovery) : LessonSection

@Serializable
data class Hunt(
    val itemId: String,
    val intro: String,
    val flags: List<HuntFlag>,
    val debrief: String,
)

@Serializable
data class HuntFlag(val hotspotId: String, val title: String, val spiderSays: String)

@Serializable
data class Recovery(
    val titleCaught: String,
    val leadCaught: String,
    val titleDefault: String,
    val leadDefault: String,
    val steps: List<Rule>,
)

@Serializable
@SerialName("status")
data class StatusSection(val question: String, val levels: List<StatusLevel>) : LessonSection

/** key: LOW | MEDIUM | HIGH */
@Serializable
data class StatusLevel(val key: String, val label: String, val persona: Persona, val reply: String)

@Serializable
@SerialName("practice")
data class PracticeSection(
    val count: Int = 8,
    val focus: List<StepKey> = emptyList(),
    val endWithHeldOut: Boolean = true,
) : LessonSection

@Serializable
@SerialName("readiness")
data class ReadinessSection(val prompt: String, val button: String, val done: String) : LessonSection
