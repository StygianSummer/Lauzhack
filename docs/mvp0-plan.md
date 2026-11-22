# ThisIsTea MVP 0: the single implementation plan

> Status: **implementation plan, single source of truth** (nathann, 2026-11-21). It replaces the
> earlier merge plan, the part 1/2/4 prompt and the part 5 build prompt (deleted; see git history).
> It is written so that one coding agent can build MVP 0 end to end from this file alone.
> Anything not specified here: pick the simplest option, note it in your handoff file.
> Changing a **contract (C1–C5)** needs a `docs/decisions.md` entry first.
> 2026-11-21 revision: consent, password findings, device data, contacts and tracking made honest
> (§1.3 items 3–5, §9.2, §9.6, §10.4, §10.6, §17; decisions.md entry of the same day).

### Satellite files (details and ideas for later; this plan wins on any conflict)

| File | What it holds | Use it for |
|---|---|---|
| [static/tea-theme.css](../static/tea-theme.css), [static/lesson-01-phishing.html](../static/lesson-01-phishing.html), [guide-rules.md](../guide-rules.md) | Design reference (tokens, components, lesson 1 copy) | Porting the look (§8) and lesson copy (§9.4). Not shipped. |
| [content/scenarios/_example.json](../content/scenarios/_example.json) | Scenario format for the later simulator | Slot vocabulary only |
| [content/resources/resources.json](../content/resources/resources.json) | "What to do now" links | Dog's cards and tools (§9.4, §9.5) |
| [docs/characters.md](characters.md), [docs/info.md](info.md) | Character backstory, storytelling pitch | Voice and tone of persona lines |
| [docs/idea.md](idea.md) | Vision, research basis, content packs | Pitch; later content |
| [docs/scenario-engine.md](scenario-engine.md) | Slots, variation operators, seed ideas | Writing items; later variation engine |
| [docs/onboarding-and-field-mode.md](onboarding-and-field-mode.md) | Background research, later field mode | Pitch; later loops |
| [docs/localization.md](localization.md) | Languages and resources beyond en/fr/es | Later languages |
| [docs/tech-stack.md](tech-stack.md) | Long-term platform split (simulator, iOS) | Later; MVP 0 has no simulator |
| [docs/awareness-sim-trigger.md](awareness-sim-trigger.md), [docs/awareness-sim-prompt.md](awareness-sim-prompt.md) | Krishna's part 5 design (opt-in trigger, reveal; later spaced series) | MVP 0 builds one email per press; the series is later (§1.4) |
| [docs/decisions.md](decisions.md) | Why things were decided | Rationale; append-only |
| [TASKS.md](../TASKS.md) | Long-term task board | MVP 0 work follows §14 |

---

## 0. How to use this file

1. Follow [AGENTS.md](../AGENTS.md): work on your own branch, never push to `main`, no real brands in
   content, `.example` domains in content, secrets only in `.env`.
2. Open satellite files (table above) only for the specific detail the plan sends you to.
3. Build in the milestone order of §14. Each milestone ends with a check you can run.
4. Where a part is owned by someone else (§1.2), build the **contract and a stub**, not the full part,
   unless your human tells you to build it all.

---

## 1. What MVP 0 is

### 1.1 The one loop we ship

```
register (verified email, password, profile, terms)
  → adaptive placement test (~3 min)
  → lesson 1: email phishing (theory sections, then practice)
  → practice with four-step prompts that fade per step as mastery rises
  → held-out email with all prompts off (the "click" moment)
  → opt-in button: "Test me: send me one practice email soon" (says plainly what will happen)
  → (server) one click-only practice email to the learner's real inbox, at a random moment
  → blame-free debrief in the app (Spider: "caught" or "dodged")
```

The four steps (UI copy, used everywhere):
1. **Who is really writing?**
2. **What feeling are they pushing?**
3. **What exactly do they want me to do?**
4. **How can I check through a channel I control?**

Internal step keys: `who`, `emotion`, `ask`, `verify`. Plus the end-of-item `verdict`.

### 1.2 Parts and owners

| Part | What | Owner | In this plan |
|---|---|---|---|
| 0 | KMP scaffold, ported theme, CI, Docker | nathann | full spec |
| 1 | Registration + profile | nathann | full spec |
| 2 | Adaptive placement test | nathann | full spec |
| 3 | Lesson 1 theory sections | nicholas | lesson JSON format + renderer spec; copy is ported from the HTML |
| 4 | Practice with faded four-step support | nathann | full spec |
| 5 | Opt-in button → one click-only practice email | krishna | contract + server flow; email text written by a human |
| — | Persona art (Cat, Turtle, Dog, Spider) | ferran + nicholas | placeholder spec; art drops into a folder |

### 1.3 Decisions this plan relies on (all recorded in decisions.md)

1. **Name:** the app is **ThisIsTea**. Outbound mail account: `thisistea@gmail.com`. ("Tea" alone collides
   with a real app that had a 2025 breach.)
2. **Stack:** Kotlin Multiplatform + Compose Multiplatform. No JS/TS front end. The HTML/CSS in `static/`
   is the **design reference**, ported into a Compose theme, never shipped.
3. **Consent is explicit and honest** (replaces the earlier "readiness button never mentions an email").
   The Terms of Use describe practice emails in their own clearly titled section. Practice emails are
   **off by default**; they are switched on only when the learner presses an end-of-lesson button that
   says in plain words that one practice email will arrive soon. What stays secret is only **when** it
   arrives and **what it looks like**, which is what the test needs. No press → no email. Settings
   always allows pause/stop.
4. **The real practice email is click-only**, from the plain Gmail account, **no custom or look-alike
   domain, no sign-in page, no credentials collected, no tracking pixel**. Only sent / clicked /
   reported is recorded.
5. **Collect only what teaching needs, and say so.** Password findings only for the password the
   learner actually sets; a short list of device settings for the UI; no contacts; every stored field
   listed in "What we know about you".
6. **Hosting:** one Docker Compose stack (`deploy/docker-compose.prod.yml`) on a machine with a public
   domain: Caddy serves the wasm build at `/` and proxies the Ktor server at `/api`, with automatic
   HTTPS; Postgres alongside. GitHub Pages stays an optional mirror. Backend URL chosen at runtime
   (`config.json` served by Caddy). Local development: `deploy/docker-compose.dev.yml`.
7. **Shared AI** (free-text grading, cached variations) behind an interface in `commonMain`, rules/stub
   fallback, optional Koog implementation. No live AI calls in the demo.

### 1.4 Out of scope for MVP 0 (do not build)

- Simulated phone frame, scenario player, other channels (SMS, calls, messengers).
- Any page that imitates a login form; any collection of what a learner types after a click.
- Resending series / spaced field emails (Krishna's later loop), look-alike domains.
- Open-tracking pixels; tracking whether or how long the terms were read.
- Uploading or storing the learner's contacts (no contact picker, no friends' emails or phones).
- Storing anything about rejected password attempts; paste detection.
- Group/team leaderboards (schema only).
- iOS build. Right-to-left languages. Languages other than en/fr/es.
- Live AI calls during the demo.

---

## 2. Tech stack and versions

Start from the **JetBrains KMP wizard** (Compose Multiplatform template, targets: Web (wasmJs),
Desktop, Android, Server). Then pin versions in `gradle/libs.versions.toml`. Use the latest stable of
each at build time; the baseline below is what this plan was written against. If a newer stable
breaks something, fall back to these.

| Library | Baseline | Where |
|---|---|---|
| Kotlin | 2.1.x | all |
| Compose Multiplatform | 1.8.x | shared, composeApp |
| kotlinx.serialization-json | 1.8.x | all |
| kotlinx.coroutines | 1.10.x | all |
| kotlinx.datetime | 0.6.x | all |
| Ktor client (core, content-negotiation, js engine for wasm, okhttp/cio for jvm/android) | 3.1.x | shared |
| Ktor server (netty, content-negotiation, cors, status-pages, call-logging, rate-limit, auth) | 3.1.x | server |
| Exposed (core, jdbc, java-time) | 0.60+ | server |
| PostgreSQL JDBC | 42.7.x | server |
| H2 | 2.3.x | server tests |
| HikariCP | 6.x | server |
| Bouncy Castle `bcprov-jdk18on` (Argon2id via `Argon2BytesGenerator`, pure Java) | 1.79+ | server |
| nbvcxz (`me.gosimple:nbvcxz`, password strength) | 1.5.x | server |
| Angus Mail (`org.eclipse.angus:angus-mail`, SMTP) | 2.0.x | server |
| Logback | 1.5.x | server |
| Koog (`ai.koog:koog-agents`), optional | latest | see §11 |
| JDK | 21 (Temurin) | build + Docker |

No DI framework, no navigation library: manual wiring and a sealed `Screen` state (§9.1). Less to break.

---

## 3. Repository layout (create exactly this)

```
settings.gradle.kts            include(":shared", ":composeApp", ":server")
build.gradle.kts
gradle/libs.versions.toml
gradle.properties              org.gradle.jvmargs=-Xmx4g, kotlin.code.style=official

shared/                        KMP library: everything that can be shared
  build.gradle.kts             targets: wasmJs { browser() }, jvm(), androidTarget()
  src/commonMain/kotlin/tea/
    model/                     Item.kt, Lesson.kt, Profile.kt, Mastery.kt, Attempt.kt, Events.kt, Dto.kt
    engine/                    Placement.kt, Bkt.kt, Scaffold.kt, Selector.kt, ShortcutBreaker.kt,
                               Traps.kt, Review.kt, Xp.kt, Calibration.kt
    content/                   ContentRepository.kt, ItemValidator.kt, Placeholders.kt
    ai/                        FourStepGrader.kt (interface + RulesGrader), VariationSource.kt
    i18n/                      Lang.kt (current language state, fallback to en)
    net/                       ApiClient.kt, BackendUrl.kt, SyncQueue.kt
    store/                     KeyValueStore.kt (expect), LocalState.kt
    ui/theme/                  TeaColors.kt, TeaType.kt, TeaShapes.kt, Persona.kt, TeaTheme.kt, HardShadow.kt
    ui/components/             PersonaBubble.kt, TeaButton.kt, TeaCard.kt, ChoiceRow.kt, ProgressBar.kt,
                               EmailView.kt, Hotspot.kt, OptionRow.kt, FeedbackCard.kt, DebugFooter.kt
    ui/screens/                App.kt (root + Screen state), onboarding/*, placement/*, lesson/*,
                               practice/*, about/*, settings/*, leaderboard/*, home/*
  src/commonMain/composeResources/
    values/strings.xml         English UI strings
    values-fr/strings.xml      French
    values-es/strings.xml      Spanish
    font/                      bundled TTFs (§8.3)
    drawable/                  persona_<name>_<mood>.png when art lands
    files/content/             GENERATED at build time from /content (do not edit, gitignored)
  src/wasmJsMain/kotlin/tea/   KeyValueStore actual (localStorage), platform info, url params
  src/jvmMain/kotlin/tea/      KeyValueStore actual (file), platform info
  src/androidMain/kotlin/tea/  KeyValueStore actual (SharedPreferences), platform info
  src/commonTest/kotlin/tea/   engine + validation tests

composeApp/                    thin app shells
  src/wasmJsMain/kotlin/main.kt      ComposeViewport(document.body!!) { App(platform) }
  src/wasmJsMain/resources/index.html, config.json (dev default)
  src/jvmMain/kotlin/main.kt         application { Window(title = "ThisIsTea") { App(platform) } }
  src/androidMain/                   MainActivity.kt, AndroidManifest.xml (INTERNET only)

server/
  build.gradle.kts             depends on project(":shared") jvm target for DTOs + engines
  src/main/kotlin/tea/server/
    Application.kt             module wiring, plugins, routes
    Config.kt                  env vars (§10.2)
    db/Tables.kt, db/Db.kt     Exposed tables (§5), migrations via SchemaUtils.createMissingTablesAndColumns
    auth/                      AuthRoutes.kt, Codes.kt, Sessions.kt, PasswordHasher.kt
    password/                  PasswordAnalyzer.kt, PwnedClient.kt
    me/                        ProfileRoutes.kt, AboutRoutes.kt, AttemptRoutes.kt, MasteryRoutes.kt,
                               EventRoutes.kt, SettingsRoutes.kt
    leaderboard/               LeaderboardRoutes.kt
    field/                     FieldScheduler.kt, FieldRoutes.kt, FieldTemplates.kt   (part 5)
    mail/                      Mailer.kt (interface), SmtpMailer.kt, ConsoleMailer.kt
  src/main/resources/logback.xml
  src/test/kotlin/tea/server/  route tests on H2 with testApplication { }

content/
  items/email/<id>.<lang>.json       in-app email items (C2)
  items/breadth/<id>.<lang>.json     news + image items
  items/breadth/img/<file>.webp      bundled images for image items
  lessons/email-01/lesson.<lang>.json  lesson (C3)
  field/<template>.<lang>.json       part 5 practice-email templates (human-written, §12)
  resources/resources.json           existing
  scenarios/_example.json            existing, untouched

deploy/
  Dockerfile, docker-compose.yml, .env.example
.github/workflows/
  pages.yml                    wasm build → GitHub Pages
  ci.yml                       tests on PRs
docs/adding-a-language.md      5 lines (§13)
```

### 3.1 Bundling content into the build

Compose resources cannot list directories, so:
- Gradle task `:shared:syncContent` (runs before `generateComposeResClass` / `copy*ComposeResources`)
  copies `content/items/**`, `content/lessons/**` and `content/resources/resources.json` into
  `shared/src/commonMain/composeResources/files/content/` and writes `files/content/index.json`:
  `{ "items": ["email/email-bank-login-01", ...], "lessons": ["email-01"], "languages": ["en","fr","es"] }`.
- Add `shared/src/commonMain/composeResources/files/content/` to `.gitignore`.
- `ContentRepository` reads `Res.readBytes("files/content/index.json")`, then each
  `files/content/items/<kind>/<id>.<lang>.json`, falling back to `.en.json` when the language file is
  missing. Images: `Res.readBytes("files/content/items/breadth/img/<file>")` → `decodeToImageBitmap()`.
- `content/field/` is **not** bundled into the app (server only).

---

## 4. Data models (commonMain, `tea.model`)

All `@Serializable`, JSON uses `snake_case` via `@SerialName`. Use one shared
`Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }`.

```kotlin
enum class StepKey { WHO, EMOTION, ASK, VERIFY }           // serialised lowercase
enum class Skill { WHO, EMOTION, ASK, VERIFY, VERDICT, BREADTH_NEWS, BREADTH_IMAGE }
enum class ItemCategory { SCAM, LEGIT }                    // misaligned_incentive / data_exposure: later
enum class ItemStatus { DRAFT, REVIEWED }
enum class Confidence { GUESSING, FAIRLY_SURE, CERTAIN }
enum class Verdict { TRICK, FINE, NOT_SURE }               // "Trying to trick me" / "Fine" / "Not sure"
enum class Context { PLACEMENT, PRACTICE, REVIEW }
enum class Support { WORKED, PROMPTED, HINT, FADED }
```

### 4.1 Item (contract C2, file `content/items/email/<id>.<lang>.json`)

```kotlin
data class Item(
  val id: String, val language: String, val status: ItemStatus,
  val kind: String,                     // "email" | "news" | "image"
  val category: ItemCategory,
  val difficulty: Int,                  // 1..6, see ladder §6.1
  val twinOf: String? = null,           // id of the scam this legit item mirrors (or vice versa)
  val heldOut: Boolean = false,
  val unsolicited: Boolean = false,
  val expectedMs: Long = 25_000,
  val use: List<String> = listOf("practice"),   // "placement", "practice", "phase_b"
  val slots: Slots,
  val email: EmailContent? = null,      // kind == email
  val news: NewsContent? = null,        // kind == news
  val image: ImageContent? = null,      // kind == image
  val hotspots: List<Hotspot> = emptyList(),
  val steps: Map<StepKey, StepContent> = emptyMap(),   // all four required for kind == email
  val linkQuestion: LinkQuestion? = null,              // "where does this link really go?"
  val actions: List<ItemAction> = emptyList(),         // ≥ 2, at least one safe
  val attackerReveal: String? = null,                  // Spider's line; for legit items: Turtle's "why it's fine"
  val resourceAsk: String? = null                      // slot ask used to pick Dog's card
)
data class Slots(val identity: String, val channel: String, val emotion: List<String>,
                 val ask: String, val surfaceClues: List<String>, val verify: String)
data class EmailContent(val fromName: String, val fromAddress: String, val replyTo: String? = null,
  val to: String = "{{email}}", val subject: String, val received: String,   // ISO-8601 duration ago, e.g. "-PT2H"
  val body: List<BodyBlock>, val attachments: List<String> = emptyList())
data class BodyBlock(val t: String, val v: String, val href: String? = null, val hotspot: String? = null)
     // t: "text" | "link" | "signature"
data class Hotspot(val id: String, val target: String, val step: StepKey,
                   val redFlag: Boolean, val explain: String)
     // target: "from_name" | "from_address" | "reply_to" | "subject" | "body:<index>" | "link" | "attachment:<index>"
data class StepContent(val prompt: String, val answer: String,
                       val choices: List<String>, val correct: Int, val cues: List<String> = emptyList())
data class LinkQuestion(val shownText: String, val options: List<String>, val correct: Int)
data class ItemAction(val id: String, val label: String, val outcome: String, val consequence: String)
     // outcome: "safe" | "unsafe" | "neutral"
data class NewsContent(val headline: String, val source: String, val date: String, val real: Boolean,
                       val tells: List<String>)
data class ImageContent(val file: String, val alt: String, val aiGenerated: Boolean, val tells: List<String>)
```

Placeholders filled from the profile at render time (`Placeholders.fill`): `{{first_name}}`,
`{{email}}`, `{{email_domain}}`. Guests: first name "Sam", email "you@mail.example".

### 4.2 Lesson (contract C3, file `content/lessons/email-01/lesson.<lang>.json`)

```kotlin
data class Lesson(val id: String, val language: String, val title: String, val subtitle: String,
                  val minutes: Int, val sections: List<LessonSection>)
@JsonClassDiscriminator("type") sealed interface LessonSection
data class ContentSection(val cards: List<RuleCard>) : LessonSection            // "content"
data class RuleCard(val persona: Persona, val role: String, val title: String, val say: String,
                    val rules: List<Rule>, val signOff: String)
data class Rule(val headline: String, val body: String)
data class ToolsSection(val intro: String, val tools: List<ToolRow>) : LessonSection  // "tools", Dog
data class ToolRow(val resourceId: String, val name: String, val what: String, val dogSays: String,
                   val button: String)          // url comes from resources.json by resourceId
data class AttacksSection(val hunt: Hunt, val recovery: Recovery) : LessonSection     // "attacks", Spider
data class Hunt(val itemId: String, val intro: String, val flags: List<HuntFlag>, val debrief: String)
data class HuntFlag(val hotspotId: String, val title: String, val spiderSays: String)
data class Recovery(val titleCaught: String, val leadCaught: String,
                    val titleDefault: String, val leadDefault: String, val steps: List<Rule>)
data class StatusSection(val question: String, val levels: List<StatusLevel>) : LessonSection  // "status"
data class StatusLevel(val key: String, val label: String, val persona: Persona, val reply: String)
data class PracticeSection(val count: Int = 8, val focus: List<StepKey> = emptyList(),
                           val endWithHeldOut: Boolean = true) : LessonSection        // "practice"
data class ReadinessSection(val prompt: String, val button: String, val done: String) : LessonSection // "readiness"
```

Order for lesson 1: `content` → `tools` → `attacks` → `status` → `practice` → `readiness`.
Nicholas's 6-question "Answers" quiz is replaced by `practice` (its questions become practice items).

### 4.3 Profile, mastery, attempts, events

```kotlin
data class Profile(
  val language: String = "en", val country: String? = null,
  val firstName: String? = null, val lastName: String? = null, val displayName: String? = null,
  val emailProvider: String? = null,                    // gmail|outlook|proton|icloud|other
  val hereFor: String? = null,                          // myself|relative|ngo|company
  val ageRange: String? = null,                         // optional: <25|25-44|45-64|65+
  val channels: List<String> = emptyList(),             // email|sms|messengers|calls|social
  val emailApp: String? = null,                         // webmail|outlook|phone_app
  val interests: List<String> = emptyList(),            // banking|parcels|jobs|investing|donations|social|
                                                        // invoices|grants|shared_accounts|beneficiary_data
  val pastExperience: String? = null,                   // none|unsure_message|lost_money|prefer_not
  val pastExperienceText: String? = null,
  val selfRating: Int? = null,                          // 1..5
  val largeText: Boolean = false, val darkMode: Boolean? = null,   // null = follow system
  val dailyMinutes: Int = 5,
  val placement: PlacementResult? = null,
  val statusRating: String? = null                      // LOW|MEDIUM|HIGH from the lesson
)
data class MasteryState(val skill: Skill, val p: Double, val level: Int, val attempts: Int,
  val unaided: Int, val support: Support, val testedOut: Boolean,
  val nextReviewAt: Instant?, val reviewInterval: Double = 1.0, val updatedAt: Instant)
data class Attempt(val clientId: String,               // UUID, idempotency key
  val itemId: String, val context: Context, val answers: AttemptAnswers, val correct: Boolean,
  val confidence: Confidence?, val scaffold: Map<StepKey, Support>, val hintsUsed: Int,
  val ms: Long, val createdAt: Instant)
data class AttemptAnswers(val verdict: Verdict? = null, val actionId: String? = null,
  val stepChoices: Map<StepKey, Int?> = emptyMap(),     // null = "I don't know yet"
  val hotspotsTapped: List<String> = emptyList(), val linkChoice: Int? = null,
  val newsForward: Boolean? = null, val imageAi: Boolean? = null, val freeText: String? = null)
data class AppEvent(val clientId: String, val type: String, val payload: JsonObject, val createdAt: Instant)
```

---

## 5. Contract C1: database and events bus

Postgres in production, H2 in tests, through Exposed. All ids are UUID unless noted. Timestamps
`timestamptz`. Tables marked **(add)** extend the original C1 additively; that is allowed without a new
decision because nothing existing changes.

```
users(id, email UNIQUE (lowercased), email_verified_at?, first_name?, last_name?, display_name?,
      language, country?, created_at, password_hash?, terms_accepted_at?, terms_version?,
      practice_emails_allowed BOOL DEFAULT false,     -- set true only by the opt-in button (§9.6)
      practice_emails_paused_at?, practice_emails_stopped_at?)
profiles(user_id PK, json, updated_at)
password_findings(user_id PK, length, classes, score, pwned BOOL, pwned_count,
      contains_name, contains_email, contains_year, keyboard_pattern,
      analysed_at)            -- the accepted password only; never plaintext; rejected attempts: nothing
client_signals(id, user_id, platform, json, created_at)   -- only the fields listed in §9.2.9
mastery(user_id, skill, p, level, attempts, unaided, support, tested_out, review_interval,
      next_review_at?, updated_at,  PK(user_id, skill))
attempts(id, client_id UNIQUE, user_id, item_id, context, answers_json, correct, confidence?,
      scaffold_json, hints_used, ms, created_at)
groups(id, name, created_at)    group_members(group_id, user_id)          -- schema only
events(id, client_id UNIQUE?, user_id, type, payload_json, created_at)   -- append-only
email_codes(id, email, code_hash, expires_at, tries, created_at, used_at?)        (add)
sessions(id, user_id, token_hash UNIQUE, created_at, last_seen_at, revoked_at?)   (add)
field_sends(id, user_id, template_id, token_hash UNIQUE, scheduled_at, sent_at?,
      clicked_at?, reported_at?, cancelled_at?, lesson_id)                        (add, part 5)
```

`DELETE /me` deletes every row for the user in every table (hard delete), then the user.

### 5.1 Event types

| Type | Emitted by | Payload |
|---|---|---|
| `registered` | server | `{language}` |
| `email_verified` | server | `{provider}` |
| `terms_accepted` | client | `{version}` |
| `onboarding_completed` | client | `{skipped: [question ids]}` |
| `placement_completed` | client | `PlacementResult` |
| `lesson_started` | client | `{lesson_id}` |
| `lesson_status_rated` | client | `{lesson_id, level}` |
| `practice_item_answered` | client | `{item_id, correct, scaffold}` |
| `held_out_answered` | client | `{item_id, correct, verdict, action_id}` |
| `lesson_completed` | client | `{lesson_id, xp, per_step: {who: p, ...}}` |
| `practice_email_opted_in` | client | `{lesson_id}` |
| `field_scheduled` / `field_sent` / `field_clicked` / `field_reported` / `field_cancelled` | server (part 5) | `{send_id, template_id}` |

`field_submitted` is reserved for a later loop; nothing emits it in MVP 0.

---

## 6. Engines (commonMain, pure Kotlin, unit-tested)

All engine functions are pure: input state + answer + `now` + `Random` → new state. No I/O. The same
code runs on client (offline) and server (recompute XP, validate).

### 6.1 Difficulty ladder (item `difficulty`)

| Level | What changes |
|---|---|
| 1 | obvious: typos, random sender, "Dear customer", raw suspicious link |
| 2 | lookalike domain, polished text |
| 3 | perfect language, the learner's first name, a known service's display name, link text ≠ target |
| 4 | header and link tricks: reply-to differs, subdomain trick (`swissalpbank.example.login-check.example`), attachment `invoice.pdf.html` |
| 5 | a real message that still uses urgency or is unsolicited, vs. a scam with no surface clue |
| 6 | hijacked real sender or ongoing thread (only step 4 helps) |

Level 7 in the placement search means "above the ladder".

### 6.2 Placement (`Placement.kt`), adapted from hopingo's `placement.js`

State: `lo=1, hi=7, idx=2, probes=0, warmupRetryUsed=false, answers=[]`.

**Phase A: find the level (max 5 probes, ~90 s).**
- A probe = a **pair** at level `idx`: one scam + its legit twin (items with `use` containing
  `placement`, same `difficulty`, linked by `twin_of`), random order, shown one at a time.
  Item mode: "Trying to trick you, or fine?" + "How sure are you?".
- `known(pair)` = both verdicts right AND neither confidence is `GUESSING` AND neither `ms > 2 × expectedMs`.
  "I don't know yet" = wrong. On probe 1 ignore time (warm-up).
- Warm-up grace: if probe 1 is not known and `!warmupRetryUsed`, show the second level-`idx` pair, set
  `warmupRetryUsed`, and use that result instead (counts as the same probe).
- Update: `if known: lo = idx + 1 else hi = idx; idx = (lo + hi) / 2` (integer division);
  `probes++`. Stop when `lo >= hi` or `probes == 5`. Boundary `level = min(lo, 6)`.
- If no unused pair exists at `idx`, use the nearest level that has one (prefer lower).

**Phase B: per-step probes at `level` (4 items, ~60 s).** Draw from items with `use` containing `phase_b`
at `level` (nearest level if none), not yet seen. One item per mode:
1. Tap the warning sign → evidence for `who` (correct if ≥ 1 red-flag hotspot with step `who` tapped and
   no more than 1 non-red-flag hotspot tapped).
2. Where does the link really go? (`linkQuestion`) → evidence for `who` and `ask`.
3. Which feeling is pushed? (`steps.emotion` choices) → `emotion`.
4. What do you do next? (`actions`; correct = outcome `safe`) → `verify`.
Mark these items as seen so practice does not repeat them this session.

**Phase C: breadth (2 items, ~30 s).** One news item ("Would you forward this?"; correct = forward only
if `real`), one image item ("Real photo or AI-generated?"). Recorded on skills `BREADTH_NEWS`,
`BREADTH_IMAGE` only; they never change email levels.

**UI rules.** Framed as "Let's see where to start". Neutral feedback only ("Got it", "We'll look at this
together"); no corrections during the test. Progress bar estimate = answered / 11, capped at 95% until
the result screen. Every item has "I don't know yet". Never show time.

**Output (`PlacementResult`)**
```kotlin
data class PlacementResult(val level: Int, val stepP: Map<StepKey, Double>,
  val hitRate: Double, val falseAlarmRate: Double,          // hit = scam flagged; FA = legit flagged
  val calibration: Double,                                   // mean |confidenceScore - correct|, 0..1; lower is better
  val overconfident: Boolean, val breadth: Map<String, Boolean>, val totalMs: Long,
  val strength: StepKey, val focus: StepKey)
```
- Confidence score: guessing 0.33, fairly sure 0.66, certain 1.0.
- `overconfident` = `selfRating >= 4` and placement `level <= 2`, OR any wrong answer with `CERTAIN`.
- Prior per step from level: `{1: 0.10, 2: 0.20, 3: 0.35, 4: 0.50, 5: 0.65, 6: 0.75, 7: 0.85}`.
  Then apply the Phase B answers as BKT updates (§6.3) to get `stepP`. `VERDICT` skill starts at the prior.
- Levels `< level` → `testedOut = true` and `nextReviewAt = now` (due for early review in practice, so a
  lucky guess is caught; as hopingo's `markKnown`).
- `strength` = step with highest p, `focus` = step with lowest p (ties: order who, emotion, ask, verify).
- Result screen copy: one strength + one focus, e.g. "You spot urgency well. Let's work on checking
  through your own channel." No score, no "out of". Button "Start lesson 1".
- Writes: mastery (all skills), `profile.placement`, event `placement_completed`.

### 6.3 Mastery: Bayesian knowledge tracing (`Bkt.kt`), same model as hopingo's `learner.js`

Constants: `SLIP = 0.10`, `LEARN = 0.12`. Guess by question type: verdict `0.5`, multiple choice
`1.0 / n`, tap-hotspot `0.2`, link question `1.0 / options.size`, action `1.0 / actions.size`.

```
update(p, correct, guess):
  posterior = if correct: p(1-SLIP) / (p(1-SLIP) + (1-p)guess)
              else:       p·SLIP / (p·SLIP + (1-p)(1-guess))
  return posterior + (1 - posterior)·LEARN
```
Evidence rules:
- Support `WORKED` → no evidence (no update, no attempt count).
- A used hint counts as **wrong**. `ms > 2 × expectedMs` counts as **wrong** (except first item of a session).
- "I don't know yet" counts as wrong.
- `unaided++` when the answer is correct, no hint, support is `PROMPTED`/`HINT`/`FADED`.
- **Mastered** = `p >= 0.95 && unaided >= 3`.
- Item difficulty for the next item: `+1` after 2 consecutive fast right items (fast = `ms <= expectedMs`),
  `-1` after 2 consecutive wrong items; clamp 1..6. Stored as `mastery[VERDICT].level`.

### 6.4 Support level per step, with hysteresis (`Scaffold.kt`)

| p | Support | What the learner sees for that step |
|---|---|---|
| < 0.35 | `WORKED` | Cat explains the step on this email; the hotspot is highlighted; "Got it" button |
| 0.35–0.6 | `PROMPTED` | Step question with 3 choices (`steps.<step>.choices`) |
| 0.6–0.8 | `HINT` | No question; a small "Need a hint?" button reveals the prompt + choices |
| ≥ 0.8 | `FADED` | Nothing for that step |

Hysteresis: move **up** a level when p crosses the upper threshold; move **down** only when p falls below
the lower threshold **minus 0.05**. Store the current `support` in mastery so it survives reloads.
Every item always ends with the **verdict** (Trying to trick me / Fine / Not sure) and the **next action**.

### 6.5 Item selection (`Selector.kt`) for one `practice` section

Input: pool (items with `use` containing `practice`, language), mastery, recent history (last 20 item
ids with correctness), shortcut stats, traps, `focus`, `count`, `endWithHeldOut`, seen-this-session, `now`, `Random`.

Per pick, score every eligible item and take the max (ties random):
- Exclude: held-out items (until the end), items seen this session unless in the "wrong, retry" queue,
  the same seed as the previous item (seed = `twin_of ?: id` root; never two of the same seed in a row),
  items whose `slots.identity` **and** `slots.ask` both equal the previous item's.
- `+3` if the item trains the weakest non-mastered step (a red-flag hotspot or step content on it), `+1`
  more if the step is in `focus`.
- `+2` if `difficulty == targetDifficulty`, `+1` if off by one, else `0`.
- `+2` if a tested-out level's review is due and `difficulty` is that level.
- `+3` if it matches the shortcut breaker's request (§6.6).
- `+2` if it exercises an active trap (§6.7).
- Legit balance: target ~40% `legit` or `unsolicited`; if the running share is below 30% give legit items
  `+3`, above 50% give them `-3`.
- **Wrong items come back:** an item answered wrong is pushed to a retry queue and re-offered after ≥ 2
  other items (`+4`), and is marked due at the start of the next session.
- After `count` items, if `endWithHeldOut`, append one item with `held_out: true` (new identity × ask
  combination) shown with **all support forced to FADED**: the "click" check.

### 6.6 Shortcut breaker (`ShortcutBreaker.kt`)

- Track `flaggedWith[clue]` = how often an item the learner judged `TRICK` contained surface clue `clue`,
  and `seen[clue]`.
- After ≥ 4 `TRICK` verdicts: if one clue appears in ≥ 70% of them and the learner flagged it ≥ 3 times,
  it "dominates". Request next: a **legit** item that **has** that clue, then a **scam** that **lacks** it.
- Request cleared once both have been shown.

### 6.7 Traps / recurring mistakes (`Traps.kt`)

- A surface feature the learner gets wrong twice becomes a trap, e.g. `trusts_display_name`,
  `trusts_first_name`, `ignores_reply_to`, `misses_subdomain_trick`, `flags_all_urgency`.
  Mapping: wrong verdict on an item → each `surface_clue` of that item gets `+1` miss
  (for legit items flagged as tricks, the clue gets `+1` false-alarm).
- Trap cleared after right answers on items with that feature on **3 different local days**.
- Traps are shown in the practice summary ("Watch out for: trusting the display name").

### 6.8 Spaced review (`Review.kt`)

Per skill: `reviewInterval` in days, starting at 1. At the end of a session, if ≥ 75% of that skill's
review answers were right: `interval *= 2.5`, else `interval = 1`. `nextReviewAt = now + interval days`.

### 6.9 XP and streak (`Xp.kt`), computed the same way on client and server

- Practice item: correct verdict `+10`, safe action chosen `+5`, each unaided correct step `+2`.
- Held-out correct (verdict right and safe action) `+30`.
- `lesson_completed` `+50`. Placement completed `+20`.
- Streak: consecutive local days with ≥ 1 practice item. No hearts, no lives.
- Server leaderboard XP = sum of `xpFor(attempt)` over stored attempts + event bonuses (so clients cannot
  send arbitrary XP numbers).

---

## 7. Content: items to create (drafts, `"status": "draft"`, for human review)

AGENTS.md: core scenarios are human-written; agents write **drafts** a human reviews. All brands
fictional, every link and address on `.example`. English first, then fr and es.

Fictional brands to use (and only these unless a human adds more): **SwissAlpBank** (bank),
**ParcelPoint** (parcels), **TeamChat** (work messenger), **CloudDrive** (file sharing),
**HelvetiCare** (health insurer), **ThisIsTea** (the app itself; real domain in items: `thisistea.example`).

### 7.1 Placement set (16 items)

| ids | level | use | notes |
|---|---|---|---|
| `pl-l1-scam`, `pl-l1-legit` | 1 | placement | pair |
| `pl-l2-scam-a`, `pl-l2-legit-a`, `pl-l2-scam-b`, `pl-l2-legit-b` | 2 | placement | two pairs (warm-up retry) |
| `pl-l3-scam`, `pl-l3-legit` | 3 | placement | pair |
| `pl-l4-scam`, `pl-l4-legit` | 4 | placement | pair |
| `pl-l5-scam`, `pl-l5-legit` | 5 | placement | pair; legit uses urgency or is unsolicited |
| `pl-l6-scam`, `pl-l6-legit` | 6 | placement | pair; ongoing thread |
| `br-news-01` | — | placement | breadth news |
| `br-image-01` | — | placement | breadth image (bundled, licence-free or generated by us) |

Phase B draws from practice items tagged `phase_b` (at least one per level 1..6, each with hotspots,
`linkQuestion`, `steps` and `actions`).

### 7.2 Practice set (20 items)

~12 scam, ~8 legit or unsolicited, levels spread 1..6, at least 6 identities (bank, parcels, work
messenger, file sharing, health insurer, the app itself) and 5 asks (`click_login`, `give_code`,
`pay_fee`, `open_attachment`, `reply_with_data`), plus **1 held-out** (`held_out: true`) whose
identity × ask combination appears in no other item (suggested: file-sharing invite from a hijacked
colleague thread × `click_login`, level 6).

Nicholas's lesson questions (q1–q6) and his two spot-check emails become practice items (rewritten
with fictional brands and `.example`).

**Seed pair (from nathann), must be included:**
- `email-bank-code-unsolicited-01` (`category: legit`, `unsolicited: true`): a short notice that someone is
  registering an account at SwissAlpBank with this address, with a verification code and "Do not share it
  with anyone". Real sender, no link. Right action: don't share the code, ignore it; if it keeps
  happening, open the bank's app yourself. Teaches: real emails can still be unwanted, and a code is never shared.
- `email-bank-login-01` (`category: scam`, its twin): classic urgency login about money moved out of the
  account, lookalike sender, link to a lookalike `.example` domain. (Shape shown in §4.1 / C2.)

### 7.3 Validation (`ItemValidator.kt`, run by a commonTest and by `:shared:syncContent`)

Build fails on:
- an item id present in `en` but missing in `fr` or `es` (warning only until fr/es are written; switch to
  error with `-Pstrict=true` in CI on `main`);
- any deny-listed real brand (case-insensitive, word match), list in `content/denylist.txt`: e.g. google,
  gmail, microsoft, outlook, apple, icloud, paypal, amazon, dhl, ups, fedex, swisspost, post.ch, ubs,
  postfinance, raiffeisen, revolut, twint, whatsapp, telegram, slack, facebook, instagram, netflix;
- any URL or address in item text whose host does not end in `.example`;
- a hotspot `target` that does not exist (e.g. `body:7` when there are 5 blocks, `reply_to` when null);
- an `email` item without all four `steps`, a step with `correct` outside `choices`, fewer than 2 actions
  or no `safe` action, a `link` block without `hotspot`;
- a `held_out` item whose (identity, ask) pair occurs in any non-held-out item;
- a placement scam without a legit twin at the same level.

---

## 8. Design system: port the HTML/CSS to Compose (`ui/theme`)

The CSS is the source of truth for values. Map it like this. **Never** hardcode a persona colour or font
in a component; read `LocalPersona`.

### 8.1 Tokens

```kotlin
object TeaColors {                       // light (narrator)
  val paper = Color(0xFFEEF2F9); val card = Color(0xFFFFFFFF); val ink = Color(0xFF111B3A)
  val inkSoft = Color(0xFF4B5775); val line = Color(0xFF111B3A); val rule = Color(0xFFC7D0E4)
  val highlight = Color(0xFFFFE66D)
  val ok = Color(0xFF1FA97A); val okDeep = Color(0xFF0C6B4B)
  val bad = Color(0xFFE5384F); val badDeep = Color(0xFF7A1424)
  // Spider's night palette (also the base for dark mode)
  val webBg = Color(0xFF1B1026); val webCard = Color(0xFF2A1A38); val webLine = Color(0xFF4A3560)
  val webInk = Color(0xFFF6E9FF); val webSoft = Color(0xFFB9A3CC)
}
// Dark mode narrator (new, keep neo-brutal: light outlines, black hard shadows)
val darkNarrator = Narrator(paper = 0xFF10172E, card = 0xFF1A2342, ink = 0xFFEEF2F9,
  inkSoft = 0xFFB4BFD9, line = 0xFFEEF2F9, rule = 0xFF3A4670, shadow = 0xFF000000, highlight = 0xFFFFE66D)
```

Spacing: `s1 = 8.dp, s2 = 16.dp, s3 = 24.dp, s4 = 36.dp, s5 = 56.dp`.
Radius: `lg = 22.dp` (cards), `md = 14.dp` (buttons, rows, inputs), `sm = 10.dp`, pill = 50%, attack panel `28.dp`.
Border: `2.dp` solid `line` on every card, button, avatar, bubble. Dividers: 2dp dashed `rule`.
Shadow: `Modifier.hardShadow(offset = 4.dp, color = ink)`; small `3.dp`. Implement with `drawBehind`
drawing the same rounded shape offset by (x, y), zero blur. Hover (desktop/web): translate (-1, -1) and
offset 6dp. Pressed: translate (4, 4) and no shadow. Attack panel: offset 6dp, colour `spider`.

Type scale (1rem = 16sp): `xs 13sp, sm 14.5sp, md 16.8sp (body, lineHeight 1.6), lg 20.8sp,
xl 25.6sp, xxl 36sp (≥ 600dp width) / 29sp compact, xxxl 56sp / 40sp compact`.
Headings: display font, lineHeight 1.1, letterSpacing -0.015em.

### 8.2 Personas (contract C4)

```kotlin
enum class Persona { CAT, TURTLE, DOG, SPIDER }
enum class Mood { NEUTRAL, HAPPY, WORRIED, SMUG }
data class PersonaStyle(val accent: Color, val deep: Color, val soft: Color, val on: Color,
  val font: FontFamily, val weight: FontWeight, val italic: Boolean, val size: TextUnit, val bubble: Shape)
```

| Persona | accent | deep | soft | on | font / weight | size | bubble (topStart, topEnd, bottomEnd, bottomStart) |
|---|---|---|---|---|---|---|---|
| CAT | #F5A524 | #9A5B00 | #FFF1D6 | ink | Space Grotesk 500 | 16.3sp | 4, 16, 16, 16 |
| TURTLE | #1FA97A | #0C6B4B | #D9F5EA | white | Fraunces 400 (titles 800) | 17.3sp | 28, 28, 28, 28 |
| DOG | #3B82F6 | #1D4FB8 | #DBE8FF | white | Fredoka 500 | 17sp | 20, 20, 20, 6 |
| SPIDER | #E5384F | #B0182E | #FFE0E5 | white | Cormorant Garamond 600 italic | 20.8sp | 2, 22, 2, 22 |

Rules carried over from guide-rules.md:
- Narrator UI (buttons, labels, nav, inputs, section headings) uses Bricolage Grotesque (headings) and
  Figtree (body). A persona font appears **only** on that persona's name tag, card title and speech.
- Text in a persona colour on a light surface uses `deep`; on dark surfaces use `accent`.
- **Spider is always dark**: his bubbles and panels use the `web*` palette in light and dark mode.
- Meaning never by colour alone: pair with a word (Correct / Not quite), an icon or strikethrough.
- Focus ring: 3dp `dog` outline, offset 3dp, on every focusable element.
- Only hero persona cards are tilted (±1–1.5°). No gradients, no blur shadows.
- Copy: British spelling, no em dashes, short sentences, second person. Turtle says "dear", Dog uses
  "!" and "buddy", Cat is terse, Spider teases with "...".

`@Composable fun PersonaBubble(persona: Persona, mood: Mood, text: String, modifier: Modifier = Modifier)`:
avatar (48dp circle, persona accent fill, 2dp border) + name + bubble in the persona's shape and font.
**Avatar placeholder until art lands:** the persona's initial letter (C/T/D/S) in the display font on the
accent circle (emoji do not render reliably on the wasm canvas). When
`drawable/persona_<name>_<mood>.png` exists, show it instead (fallback to `neutral`, then the letter).
Role of each persona in MVP 0: **Cat** step prompts and hints; **Spider** `attackerReveal` after scam
items and the debrief; **Turtle** "why it's fine" after legit items and recovery steps; **Dog** the
"what to do now" card and tools.

### 8.3 Fonts (bundle; the canvas cannot use web fonts)

Download the static TTFs from Google Fonts (all SIL OFL; add `OFL.txt` next to them) into
`composeResources/font/`: `bricolage_grotesque_semibold.ttf`, `bricolage_grotesque_extrabold.ttf`,
`figtree_regular.ttf`, `figtree_medium.ttf`, `figtree_semibold.ttf`, `figtree_bold.ttf`,
`space_grotesk_medium.ttf`, `fraunces_regular.ttf`, `fraunces_extrabold.ttf`, `fredoka_medium.ttf`,
`cormorant_garamond_semibold.ttf`, `cormorant_garamond_semibold_italic.ttf`. Monospace (domains/URLs):
`FontFamily.Monospace`.

### 8.4 Theme switches

`TeaTheme(dark: Boolean, largeText: Boolean)`: provides narrator colours, `LocalPersona` styles, and when
`largeText` multiplies `LocalDensity.fontScale` by 1.25. `dark` defaults to the system preference
(`isSystemInDarkTheme()`), overridable in Settings and persisted. Minimum touch target 48dp.

---

## 9. App (Compose, `ui/screens`)

### 9.1 Navigation and state

`App(platform: PlatformInfo)` holds an `AppState` (a class with `MutableStateFlow`s), created once:
`screen: Screen`, `session: Session?`, `profile`, `mastery`, `lang`, `settings`, `syncStatus`.

```kotlin
sealed interface Screen {
  data object Language; data object Email; data class Code(val email: String); data object Name
  data object Password; data class Onboarding(val q: Int); data object Terms
  data object PlacementIntro; data object Placement; data object PlacementResult
  data object Home; data class Lesson(val id: String, val section: Int)
  data object PracticeSummary; data class Reveal(val token: String)
  data object About; data object Settings; data object Leaderboard
}
```
Start screen: `Home` if a profile with `placement` exists locally, else `Language`. Back = previous
screen in a simple stack. On web, mirror the screen in `location.hash` (`#/home`, `#/reveal/<token>`)
so the practice-email link can open the reveal screen.

Layout: content column max width 720dp, centred, `s2` side padding. Compact (< 600dp): single column.
Every screen has a bottom `DebugFooter` (small, `inkSoft`): active backend URL, online/offline, build sha.

### 9.2 Part 1 screens: registration and profile

Principle: collect what helps teaching, ask for as little as possible up front, one question per
screen, big buttons, everything skippable except email, code, password and terms.

1. **Language**: en / fr / es buttons, preselected from the locale; country dropdown (preselected from
   locale/timezone; list: CH, FR, ES, DE, IT, GB, IN, PK, Other). Switching language re-renders
   immediately (no restart).
2. **Email**: field + "Send me a code". `POST /auth/start`. Offline → button "Continue without an account"
   (local guest; skip to Onboarding). Errors: invalid address, rate-limited ("Please wait a minute").
3. **Code**: 6 digits, auto-advance, "Send a new code" after 60 s. `POST /auth/verify` → session token.
   Shows: "We will never ask you for this code or your password in an email or a message."
4. **Name**: first name (required for personalisation), last name (optional), display name for the
   leaderboard (default `First L.`).
5. **Password**: one field, show/hide toggle, min 10 characters, no composition rules. A visible line
   under the field: "We check your password against known data breaches without sending it anywhere in
   full, and we keep a few facts about the one you choose (like its length) for your password lesson.
   Never the password itself." Length is checked on the client; the submit goes to
   `POST /auth/password/attempt`. Messages say the real reason: too short → "At least 10 characters,
   please."; found in a breach → "This password appears in known data breaches. Please choose another
   one." Rejected attempts are not stored in any form. Never store the password client-side; clear the
   field state after the call.
6. **Onboarding** (one per screen, all skippable, "Skip" top-right): I'm here for; age range (optional);
   channels I use (multi); which email app; what I do online (multi; NGO options shown if here-for is
   NGO/company); "Ever had a message you weren't sure about, or lost money?" (none / not sure about one /
   lost money / prefer not to say + optional text, which switches feedback to the gentle tone); "How good
   are you at spotting scams?" 1–5; bigger text? (applies immediately); daily goal 2/5/10 min.
   Emit `onboarding_completed`.
7. **Terms**: required checkbox "I accept the terms of use", collapsed "Read the terms" link expanding a
   scrollable text (`strings.xml` key `terms_body`, versioned `terms_version = "2026-11-21"`). Below the
   checkbox, always visible, a short plain summary of what we store, linking to the full terms. The
   terms have a clearly titled section **"Practice emails"**: "Only if you ask for it at the end of a
   lesson, we send one practice phishing email to the address you registered. You won't know exactly
   when it arrives or what it looks like; that is the test. It never asks for a password, and we only
   record whether its link was clicked and whether you reported it. You can pause or stop practice
   emails any time in Settings." Emit `terms_accepted`. Accepting the terms does **not** switch practice
   emails on.
8. **Permissions: none at signup.** Web: no notification/location/camera prompt. Android: INTERNET only.
9. **Device settings** (needed to adapt the UI; listed in "What we know about you"), `POST /me/signals`
   once after terms: platform (web/desktop/android), browser family (not the full user agent), device
   class (compact/medium/expanded), touch vs mouse, locale, timezone, dark-mode preference, reduced
   motion (`matchMedia`), font scale. Nothing else (no referrer, no timing, no fingerprinting).

**"What we know about you"** (`About`, from Home and Settings): `GET /me/about` returns a list of
`{field, value_in_plain_words, purpose}` covering **every** stored field, e.g. "Your password is 12
characters long and uses 3 kinds of characters. Purpose: your password lesson."
Includes "Delete my account" (confirm dialog → `DELETE /me` → clear local store → Language screen).
Offline/guest: show local data only.

### 9.3 Part 2 screens: placement

`PlacementIntro` ("Let's see where to start. About 3 minutes. No score.") → `Placement` (one item per
screen; modes §6.2) → `PlacementResult`. All client-side; attempts saved locally with
`context = PLACEMENT` and queued for sync.

`ItemRenderer(item, mode, support: Map<StepKey, Support>, onAnswer)` is **one** component used by
placement and practice. Modes: `VERDICT`, `TAP_HOTSPOT`, `LINK`, `EMOTION`, `ACTION`, `NEWS`, `IMAGE`,
`FULL` (practice: steps per support + verdict + action).

`EmailView(email, hotspots, tappable, highlighted)`: a generic mail-client look (no real-brand look):
header rows From (name + address), Reply-To (only if set), To, Subject, relative time; body blocks;
attachments as chips. Links render as underlined blue text; **hover (desktop/web) or long-press
(touch) shows the real target** in a small mono tooltip, like a real mail client. Links are never real
navigation. In `TAP_HOTSPOT` mode every hotspot target is tappable (plus 2 non-flag decoy regions:
greeting and signature); tapped regions get a `highlight` background and a Spider-coloured outline.

"How sure are you?" row after each placement answer: Guessing / Fairly sure / Certain.

### 9.4 Part 3 screens: lesson 1 (renderer is ours, copy is Nicholas's)

`LessonScreen` renders `lesson.<lang>.json` section by section with a top progress row of the section
titles (done / current), mirroring the HTML topbar. Port all copy from
`static/lesson-01-phishing.html` (already uses `thisistea.example`, look-alike `.example` domains and the
fictional TeamChat / CloudDrive; persona "Sam" stays). Remove the "Demo toggle"; Spider's practice-email
banner appears only when the learner asked for a practice email (§9.6).
- **content**: two rule cards side by side (≥ 600dp) or stacked: Cat (personal), Turtle (organisational),
  3 rules each, sign-off. Tap a card → enlarged dialog.
- **tools**: Dog intro bubble + tool rows (icon letter, name, what, Dog's line, button opening the URL from
  `resources.json` in the system browser). Only resources with a URL.
- **attacks** (dark Spider panel): the interactive hunt on the item `hunt.itemId` (tap to find the 3
  flagged hotspots; decoys count as "wrong guesses"; "Show me the rest" gives up; found flags unlock
  Spider's explanation), the domain difference (real vs fake, differing characters in `spider` colour,
  mono), Cat's link back to her rules, then Turtle's recovery checklist (ticks persisted locally under
  `tea.recovery.email-01`). Recovery title/lead use the `Caught` variant only if a `field_clicked` event
  exists for the user, else the `Default` variant.
- **status**: 3-way choice Still unsure / Getting there / Confident; reply from Turtle / Cat / Spider;
  emits `lesson_status_rated`; HIGH or LOW feeds the overconfidence flag (`profile.statusRating`).
- **practice**: §9.5.
- **readiness**: §9.6.
Until Nicholas's JSON exists, ship a stub lesson with one short placeholder per section.

### 9.5 Part 4 screen: practice inside the lesson

Loop per item (`PracticeScreen`):
1. Show the email (`EmailView`) and, for each step in order who → emotion → ask → verify, the UI dictated
   by that step's `Support` (§6.4). WORKED: Cat bubble with `steps.<s>.answer`, hotspot highlighted,
   "Got it". PROMPTED: question + 3 choices; a wrong choice is struck out and disabled, a hint
   (`steps.<s>.prompt` + cues) appears, one retry; second miss reveals the answer (no answer on the first
   miss, as in the HTML quiz). HINT: "Need a hint?" chip reveals the PROMPTED UI (counts as hint used).
   FADED: nothing.
2. Verdict: Trying to trick me / Fine / Not sure. Then next action (shuffled `actions`).
3. Optional at FADED: "Why? (optional)" one-line text box, graded by `FourStepGrader` (§11), shown as
   ticks per step; does not change mastery in MVP 0.
4. Feedback card: right/wrong without blame ("Correct" / "Not quite"); Spider's `attackerReveal` (scam)
   or Turtle's explanation (legit); the consequence of the chosen action; the four steps filled in for
   this email. If the learner chose an `unsafe` action, Dog shows the matching "what to do now" card
   from `resources.json` (by `resourceAsk ?: slots.ask` × profile country, fallback `ALL`; show only
   `verified: true` entries, or all with a "link not yet checked" label in dev builds).
5. Update BKT per step, support levels, traps, shortcut stats, XP; save attempt; next item from the selector.

Top bar during practice: per-step mini bars (4 bars labelled with the step names) so the fading is visible,
XP counter, streak flame + number. **Demo aid:** in debug builds, a long-press on the XP counter shows
the raw p per step.

End of section: held-out item (all FADED) → `PracticeSummary`: per-step progress bars (before → after),
XP earned, streak, traps to watch, then `lesson_completed`.

Leaderboard (`Leaderboard` screen, from Home and the summary): global weekly / all-time XP tabs,
top 20 + "you". Hidden when offline. Button **"Invite a friend"** copies the app link (system share
sheet where available). The app never reads, uploads or stores anyone's contacts.

### 9.6 Opt-in button (end of lesson; the consent for the practice email)

The lesson ends with "Lesson complete" either way. Below it, an optional card:
- `readiness.prompt`: "Want to test yourself for real? We'll send **one practice phishing email** to
  {{email}} at a random moment in the next few minutes. It will look like a real scam but it is
  harmless and never asks for a password. See if you spot it."
- `readiness.button`: "Yes, send me one". A secondary text button "Not now" closes the card.
- On press: emit `practice_email_opted_in`, call `POST /me/readiness {lesson_id}`, then show
  `readiness.done` from the server answer: scheduled → "Done. Keep an eye on your inbox, and use the
  four steps."; not scheduled → the reason in plain words (e.g. "Practice emails are paused in
  Settings.", "You already did this lesson's test.", "Verify your email first.").
- Never say when it will arrive or what it will look like (that is the test); never hide **that** it
  will arrive. Offline: the button is disabled with "Needs a connection".

### 9.7 Home, Settings, Reveal

- **Home**: "Continue lesson 1" / "Practise again" (new practice section from the selector), streak, XP,
  leaderboard, "What we know about you", Settings, and a **"Got a suspicious email?"** button
  (`POST /me/field/report`, emits `field_reported` for the latest unreported send; always answers with
  the careful wording: "If it came from us, well done for checking. If not, it could be a real scam: let's
  go through the four steps."). A Spider debrief card appears when the latest field send was
  clicked ("caught", links to Reveal) or reported ("dodged").
- **Settings**: language, dark mode (system/light/dark), large text, daily goal, **Practice emails:
  allowed / paused / stopped** (`PUT /me/settings`; shows whether a practice email is pending, without
  its time), delete account, active backend URL with a field to change it.
- **Reveal** (`#/reveal/<token>`, reached from the practice email's link): short and blame-free: "This was
  the ThisIsTea practice email you asked for. We only stored that the link was clicked." Then the four
  steps for that template, the hints that gave it away (from the template, §12) and one button "Practise
  this pattern" (starts a practice section focused on the learner's weakest step).

---

## 10. Server (Ktor)

### 10.1 Plugins and conventions

ContentNegotiation (the shared `Json`), CallLogging (never log bodies of `/auth/*`), StatusPages
(`{error: code, message}`), CORS (`ALLOWED_ORIGINS` exact origins + any `http://localhost:*`; methods GET,
POST, PUT, DELETE; header `Authorization`, `Content-Type`), RateLimit, Bearer auth by session token
(`Authorization: Bearer <token>`; look up `sha256(token)` in `sessions`, update `last_seen_at`).
`GET /health` → `{status: "ok", db: true|false, mailer: "smtp"|"console", version}`.

### 10.2 Configuration (`.env`, never committed; `deploy/.env.example` committed)

```
DATABASE_URL=jdbc:postgresql://db:5432/tea
DATABASE_USER=tea
DATABASE_PASSWORD=change-me
PUBLIC_URL=https://<domain>                           # one origin: app at /, API at /api
ALLOWED_ORIGINS=                                       # extra origins only (e.g. the Pages mirror)
APP_PUBLIC_URL=                                        # links inside emails; default PUBLIC_URL/
API_PUBLIC_URL=                                        # click links inside emails; default PUBLIC_URL/api
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=thisistea@gmail.com
SMTP_PASSWORD=                                         # Gmail app password; empty → ConsoleMailer
SMTP_FROM_NAME=ThisIsTea
FIELD_DELAY_MIN_S=15
FIELD_DELAY_MAX_S=90
OPENROUTER_API_KEY=                                    # optional, AI variations offline only
```

### 10.3 Endpoints

| Method + path | Auth | Request → response | Notes |
|---|---|---|---|
| `GET /health` | – | → status | |
| `POST /auth/start` | – | `{email, language}` → `204` | creates user if new (`registered`); new 6-digit code, hashed (SHA-256 + server pepper), 10-min expiry; rate limit 1/min and 5/hour per email, 20/hour per IP; sends mail |
| `POST /auth/verify` | – | `{email, code}` → `{token, user_id, has_password}` | 5 tries per code; sets `email_verified_at` (`email_verified`, derive `email_provider`); creates session |
| `POST /auth/password/attempt` | session | `{password}` → `{accepted, reason?}` | §10.4; `reason`: `too_short` \| `breached` |
| `POST /auth/login` | – | `{email, password}` → `{token}` | argon2id verify; 10/hour per email |
| `POST /auth/logout` | session | → `204` | revoke |
| `GET /me/profile`, `PUT /me/profile` | session | `Profile` | name fields also copied to `users` |
| `POST /me/signals` | session | `{platform, json}` → `204` | |
| `GET /me/about` | session | → `[{field, value, purpose}]` | plain-words summary |
| `DELETE /me` | session | → `204` | hard delete everything |
| `POST /me/attempts` | session | `[Attempt]` → `{stored, duplicates}` | batched, idempotent on `client_id` |
| `GET /me/mastery`, `PUT /me/mastery` | session | `[MasteryState]` | last-write-wins by `updated_at` |
| `POST /me/events` | session | `[AppEvent]` → `204` | idempotent on `client_id`; rejects `field_*` types |
| `GET /me/events?type=field_*` | session | → `[event]` | prefix match on `*` |
| `PUT /me/settings` | session | `{practice_emails: allowed|pause|stop, ...}` | `pause`/`stop` cancel pending sends |
| `GET /leaderboard?period=week|all` | optional | → `{top: [{display_name, xp, rank}], me?}` | XP per §6.9; week = since Monday 00:00 Europe/Zurich |
| `POST /me/readiness` | session | `{lesson_id}` → `200 {scheduled, reason?}` | part 5, §10.6; `reason`: `paused` \| `stopped` \| `not_verified` \| `already_done`; never returns the send time |
| `POST /me/field/report` | session | → `204` | marks latest unreported send as reported |
| `GET /f/c/{token}` | – | → `302 APP_PUBLIC_URL#/reveal/{token}` | `field_clicked` |
| `GET /field/reveal/{token}` | – | → `{template_id, steps, hints}` | read by the Reveal screen |
| `POST /admin/field/cancel` | `X-Admin-Key` | `{user_id}` | demo reset |

### 10.4 Password analysis (`PasswordAnalyzer.kt`), in memory only

Input: the plaintext password, the user's first name, email local part.
- `pwned` / `pwned_count`: Have I Been Pwned Pwned Passwords range API: SHA-1 the password, send only
  the first 5 hex chars to `https://api.pwnedpasswords.com/range/<prefix>` with header `Add-Padding: true`
  and a generic `User-Agent: ThisIsTea-server`, match the suffix locally. Timeout 2 s; on failure
  `pwned = false` and log "hibp unavailable".
- Accept iff `length >= 10 && !pwned`. **Rejected: answer with the reason, store nothing.**
- **Accepted only:** store in `password_findings` (one row per user, replaced on password change):
  `length`, `classes` (count of lower/upper/digit/symbol present), `score` (nbvcxz 0–4),
  `keyboard_pattern` (nbvcxz pattern type or a qwerty/azerty/qwertz row-walk check),
  `contains_name`, `contains_email` (case-insensitive, leetspeak-normalised), `contains_year`
  (`19\d\d|20\d\d`), `pwned` / `pwned_count`. All of it is shown in "What we know about you".
- On accept: `password_hash = argon2id(password)` with Bouncy Castle
  (`m = 19456 KiB, t = 2, p = 1`, 16-byte random salt, 32-byte hash, stored as a PHC string
  `$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`).
- The plaintext is never logged, stored, returned or kept after the request (clear the CharArray).

### 10.5 Mailer

`interface Mailer { suspend fun send(to: String, subject: String, text: String, html: String?) }`.
`SmtpMailer` (Angus Mail, STARTTLS 587, from `SMTP_FROM_NAME <SMTP_USER>`) when `SMTP_PASSWORD` is set,
else `ConsoleMailer` (prints to the log, dev only). Verification email: subject "Your ThisIsTea code",
body with the code, 10-minute validity, and "We will never ask you for this code or your password in an
email or a message." Localised (en/fr/es) from server-side string maps.

### 10.6 Part 5: field scheduler (contract with krishna)

On `POST /me/readiness`:
1. Emit `practice_email_opted_in` (if the client has not already sent it, deduplicated).
2. Check: `email_verified_at` set, `terms_accepted_at` set, not paused, not stopped, and no previous
   `field_sends` row for this user and lesson. If one fails, return `{scheduled: false, reason}` and do
   nothing else. Otherwise set `practice_emails_allowed = true` (this press is the consent) and return
   `{scheduled: true}`.
3. Insert `field_sends` with a random 32-byte token (store only its SHA-256), `template_id =
   "practice-email-01"`, `scheduled_at = now + uniform(FIELD_DELAY_MIN_S, FIELD_DELAY_MAX_S)` seconds;
   log the delay at INFO; emit `field_scheduled`.
4. `FieldScheduler` is a coroutine in the application scope: every 5 s it picks due, unsent, uncancelled
   rows (so pending sends survive a restart), renders the template (§12) in the user's language with
   `{{first_name}}`, the click URL and the settings URL, sends through `Mailer`, sets `sent_at`, emits
   `field_sent`. If paused/stopped meanwhile: cancel, emit `field_cancelled`.
5. Click / report endpoints set the timestamps once and emit the matching events.
One press = at most one email per lesson. No resend series in MVP 0.

---

## 11. Shared AI logic (frugal, optional)

```kotlin
interface FourStepGrader { suspend fun grade(item: Item, text: String): Map<StepKey, Boolean> }
class RulesGrader : FourStepGrader   // default: per step, true if text matches any of steps.<s>.cues
                                     // or slot keywords (normalised, stemmed by simple suffix stripping)
interface VariationSource { fun variationsOf(seedId: String): List<Item> }   // reads bundled cached files
```
- The app always runs with `RulesGrader` and the bundled items. No AI call is needed to run or demo.
- Optional `KoogGrader` using Koog with a small model via OpenRouter, **only on the server** (key stays in
  `.env`), exposed as `POST /ai/grade`; the client uses it only if the server reports it in `/health`,
  with a 3 s timeout falling back to `RulesGrader`. If Koog supports the wasm target at build time, the
  interface stays in `commonMain` and the implementation may move there; otherwise keep it server-side.
- Variation generation is an **offline** Gradle task `:server:generateVariations` (stretch): seed + target
  slots → small model writes text → `ItemValidator` → saved as `status: "draft"` for human review. Never
  runs in the demo.

---

## 12. Contracts C2–C5 summary, plus the field template

- **C2 item format:** §4.1. **C3 lesson hook:** §4.2 + §9.4. **C4 persona hook:** §8.2.
- **C5 backend URL** (`BackendUrl.kt`): resolve in order (1) `?api=<url>` query parameter, which is then
  saved to localStorage key `tea.api`; (2) saved `tea.api`; (3) `config.json` fetched relative to the page
  (`{ "api": "https://..." }`, written by the Pages workflow from the repo variable `API_URL`);
  (4) `http://localhost:8080`. `?api=reset` clears the saved value. Desktop/Android: env var
  `TEA_API`, then settings value, then `http://localhost:8080` (Android emulator: `http://10.0.2.2:8080`).
  Show the active URL in the debug footer.
- **Offline behaviour:** `ApiClient` pings `/health` at start and every 30 s while offline. Offline:
  registration offers "Continue without an account" (guest), leaderboard hidden, calm banner "Offline:
  your progress stays on this device". `SyncQueue` stores pending attempts/events/mastery in the
  KeyValueStore and flushes them (batched, idempotent) when online and logged in. A guest who registers
  later uploads their queue.
- **Field template** (`content/field/practice-email-01.<lang>.json`, server only, owned by krishna):
  `{ id, subject, text, html, reveal: { steps: {who, emotion, ask, verify}, hints: [..] } }`. The text is
  **written and reviewed by a human**, low-emotion (no threats, no family emergencies), sent honestly from
  the plain Gmail account, does not imitate any real company or service, contains exactly one practice
  link (`{{click_url}}`), no tracking pixel, and a small footer line "Manage ThisIsTea emails"
  linking to Settings (`{{settings_url}}`). Agents do not write this text; ship a placeholder template that
  says "Placeholder practice email: replace before the demo" until krishna provides it.

---

## 13. Localization

- UI strings: Compose resources `values/strings.xml` (en), `values-fr/`, `values-es/`. Key naming:
  `<screen>_<thing>` (e.g. `onboarding_here_for_title`, `practice_verdict_trick`, `step_who`). All four
  step names, verdicts, confidence levels and persona names are strings.
- Content: one file per language `<id>.<lang>.json`, fallback to `en`.
- Language switch without restart: `AppState.lang` drives a `LocalLanguage`; on wasm/jvm set
  `Locale` for resources through the Compose resources environment (re-composition on change).
- **Adding a language = adding files, no code change.** Write `docs/adding-a-language.md` (5 lines):
  copy `values/strings.xml` to `values-<lang>/`, translate; copy each `content/**/<id>.en.json` to
  `<id>.<lang>.json`, translate text fields only; add the code to `content/languages.txt`; run tests.

---

## 14. Build order (milestones, each one reviewable as a small PR)

| # | Milestone | Check |
|---|---|---|
| M0 | Scaffold from the wizard, modules `shared/composeApp/server`, versions pinned, `.gitignore` updated, ported theme + fonts + `PersonaBubble` + a theme gallery screen (all components, light/dark) | `./gradlew :composeApp:run` shows the gallery; `wasmJsBrowserDevelopmentRun` too |
| M1 | Models, content sync task, validator, 2 seed items + stub lesson | `./gradlew :shared:allTests` green; validator rejects a planted bad item |
| M2 | Engines §6 with tests | all engine tests green |
| M3 | Server skeleton: DB, `/health`, auth (start/verify/password/login), mailer, CORS, Docker Compose | `docker compose up` → `/health` ok; register via curl; code in the log |
| M4 | Part 1 screens + local store + `ApiClient` + C5 + sync queue | register end to end against local server; guest mode with server down |
| M5 | Part 2 placement screens with the 16 placement items | ~3 min run, result screen, mastery stored |
| M6 | Part 4 practice + lesson renderer (stub lesson) + summary + leaderboard + invite link | prompts visibly fade; held-out at end; leaderboard shows the user |
| M7 | Part 5 opt-in button + field scheduler + reveal + report + settings | ConsoleMailer prints the email 15–90 s after the press; no press → no email; click link → reveal; pause stops it |
| M8 | Pages + CI workflows, Android shell, fr/es strings, About screen, polish | Pages workflow green; app opens on Android |
| M9 | Nicholas's lesson JSON ported, 20 practice items, krishna's template, art drop-in | full demo run (§16) |

---

## 15. Deployment files (write these)

`deploy/Dockerfile`:
```dockerfile
FROM gradle:8.10-jdk21 AS build
WORKDIR /src
COPY . .
RUN gradle :server:installDist --no-daemon
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
COPY content/field /app/content/field
EXPOSE 8080
CMD ["/app/bin/server"]
```

`deploy/docker-compose.yml`:
```yaml
services:
  db:
    image: postgres:16
    environment: { POSTGRES_DB: tea, POSTGRES_USER: tea, POSTGRES_PASSWORD: ${DATABASE_PASSWORD} }
    volumes: [ "tea-db:/var/lib/postgresql/data" ]
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U tea"], interval: 5s, retries: 10 }
  app:
    build: { context: .., dockerfile: deploy/Dockerfile }
    env_file: ../.env
    ports: [ "8080:8080" ]
    depends_on: { db: { condition: service_healthy } }
volumes: { tea-db: {} }
```

**Superseded (no tunnel):** the files above are the original sketch. What is built: `deploy/Dockerfile`
with two targets (`server`, `web` = Caddy + the wasm build), `deploy/Caddyfile` (app at `/`, API at
`/api`, `config.json` generated from `PUBLIC_URL`), `deploy/docker-compose.dev.yml` (local, no `.env`;
profile `web` adds Caddy on http://localhost:8081) and `deploy/docker-compose.prod.yml` (Caddy on
80/443 with automatic HTTPS; the app port is not published). See README "Deploy".

`.github/workflows/pages.yml`: on push to `main` (and `workflow_dispatch`): checkout → setup-java 21 →
gradle cache → `./gradlew :composeApp:wasmJsBrowserDistribution -Pstrict=true` → write
`composeApp/build/dist/wasmJs/productionExecutable/config.json` with `{"api":"${{ vars.API_URL }}"}` →
`actions/upload-pages-artifact` (that folder) → `actions/deploy-pages`. Permissions: `pages: write`,
`id-token: write`. Requires Settings → Pages → Source: GitHub Actions.

`.github/workflows/ci.yml`: on pull_request: `./gradlew :shared:allTests :server:test`.

---

## 16. Definition of done (the demo run)

1. `docker compose -f deploy/docker-compose.prod.yml up -d --build` on the demo host (or locally
   `deploy/docker-compose.dev.yml` plus `:composeApp:wasmJsBrowserDevelopmentRun`).
2. A new user registers with a real email, receives the code, enters it, sets a password (a breached
   one is refused with the reason; only the accepted one leaves findings), answers onboarding, accepts
   the terms.
3. Finishes placement in about 3 minutes and sees one strength and one focus.
4. Plays lesson 1: content, tools, attacks hunt, status, practice where prompts visibly fade as they answer
   correctly, ends with the held-out email, sees the summary, appears on the global leaderboard.
5. Presses "Yes, send me one" (which says plainly that one practice email is coming). 15–90 s later the
   practice email arrives from `thisistea@gmail.com`; clicking its link opens the in-app Reveal; Home
   shows Spider's debrief. Pausing in Settings before the send stops it. Without the press, no email.
6. With the backend down, placement and practice still work as a guest and sync later.
7. The database holds the user, profile, findings for the accepted password only (no plaintext, no hash
   besides the argon2id), the device settings of §9.2.9, mastery, attempts and the C1 events, including
   `field_*`. Every stored field appears in "What we know about you".
8. Unit tests pass: placement search + stopping rule + warm-up retry, BKT update + evidence rules,
   support level + hysteresis, selector (no same seed twice, legit share, retry queue, held-out last),
   shortcut breaker, traps, XP, password analysis (HIBP mocked), item validation, route tests for auth and
   attempts idempotency.
9. UI works in en, fr and es; adding a language needs files only; large-text and dark mode work.
10. Runs on ≥ 2 platforms: web (wasm) and desktop (jvm); Android shell builds.
11. The Pages workflow is green on `main`. README states what is shared in `commonMain` (models, engines,
    content, i18n, theme, UI screens, AI interface) vs platform-specific (AppShell, KeyValueStore, HTTP
    engine, platform info, share sheet).

---

## 17. Safety and privacy rules (non-negotiable)

- **Two kinds of "email", kept separate.** (a) In-app email items: fictional teaching content, fictional
  brands, `.example` only. (b) The one real practice email: from our plain Gmail account, no look-alike
  domain, click-only, no sign-in page, no tracking pixel, nothing typed is collected. Neither imitates a
  real service.
- **Honesty test for every screen:** if a learner would feel tricked when they find out what we did
  (not *when* the practice email came, but *that* it came, or what we stored), change the design.
- Practice emails are off by default. They need accepted terms **and** an explicit opt-in press whose
  text says one practice email will come; pause/stop in Settings at any time; one email per press.
- Passwords: plaintext only in memory during analysis; argon2id hash and findings for the accepted one
  only; nothing for rejected attempts. Codes hashed, short-lived, rate-limited.
- No contacts read or stored; device settings limited to §9.2.9.
- No secrets in logs, images, the web build or git. `.env` is gitignored; `.env.example` has no values.
- Every stored field has a purpose shown in "What we know about you"; delete-my-account works.
- Core content human-written; AI only drafts variations for review; no live AI calls in the demo.
- Do not send outbound requests that include user data except HIBP's 5-char hash prefix.

---

## 18. Git route to `main`

1. This plan lands on `nathann`, then a PR `nathann → main` opened manually on GitHub (gh is not
   installed here), reviewed by a teammate.
2. After merge everyone runs `git merge origin/main` into their branch.
3. Code lands per milestone (§14) in small reviewed PRs, in that order.

## 19. Open items to confirm (do not block the build on them)

- The Gmail account exists and has an app password; Gmail SMTP volume is fine for a few team addresses.
- Krishna: confirm the `field_sends` table and scheduler in §10.6 replace the in-memory scheduler, and
  write `practice-email-01` text with the team. Your `awareness-sim-*` docs were revised to match §9.6
  and §17 (honest opt-in, plain sender, no login form); please confirm.
- Nicholas: confirm §4.2 maps his five sections (the "answers" quiz becomes practice items).
- Ferran: confirm the name ThisIsTea; deliver art as `persona_<name>_<mood>.png` (neutral, happy,
  worried, smug; 256×256, transparent).
- Facts marked ⚠ in other docs stay off slides until checked.
