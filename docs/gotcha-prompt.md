# Build prompt: "caught" reminder + gotcha intercept (loop 2)

> A ready-to-paste prompt for a coding agent to build the second loop of the practice email:
> what happens **after** the learner clicks it. The trigger, reveal and report already exist
> ([awareness-sim-trigger.md](awareness-sim-trigger.md)).
> **The plan wins:** [docs/mvp0-plan.md](mvp0-plan.md) §9.6–9.7, §10.5–10.6, §12, §17.

---

You are building one piece of **ThisIsTea, a cyber security literacy course** (LauzHack hackathon).
Learners can ask for a harmless practice phishing email. When a learner **clicks** that email, we
already record `field_clicked` and show a short reveal page. Now the loop continues: the app pulls
them back into the lesson they were tested on, and the lesson confronts them — through the Spider —
with what they did. The three rules of the practice email still hold in your code:

1. **The learner asked for it, knowingly.** The reminder email links back to our app, nothing else.
2. **Nothing is imitated.** The reminder comes from our own plain address, same sender as the
   practice email. Fictional names only.
3. **Nothing is collected.** The gotcha interview is multiple choice only: no form, no typed data,
   no secrets. We only show the learner facts we actually recorded.

## Stack

- **Kotlin.** Server: **Ktor**. UI: **Compose Multiplatform** (web + desktop; Android if the SDK is
  present). No new languages, no new dependencies.
- i18n is mandatory: every user-visible string goes into
  `shared/src/commonMain/composeResources/values{,-es,-fr}/strings_*.xml` in **all three locales**
  (en, fr, es). New keys in `strings_learn.xml`, named `gotcha_*` / `lesson_pending_*`.
- Reuse the existing components and tokens: `SpiderPanel`, `PersonaBubble(Persona.SPIDER, …)`,
  `VoiceLine`, `TeaCard`, `TeaButton`, `ChoiceRow`, `SectionHeading`, `ScreenColumn`, `EmailView`,
  `TeaColors.spider*` / `web*` and the persona font system. The Spider's font appears only on things
  the Spider owns. Don't invent parallel styling.

## What already exists (build on it, don't rebuild it)

- `field_sends` rows carry `lesson_id` and `token_hash`; `clicked_at`, `reported_at`,
  `cancelled_at` are already filled by `/f/c/{token}` and `/me/field/report`
  (`server/src/main/kotlin/tea/server/field/`).
- `FieldScheduler` already runs a poll every `FIELD_POLL_S` seconds over that table — extend it,
  don't add a second scheduler.
- `FieldTemplates` loads `content/field/<id>.<lang>.json` with `.en` fallback and re-reads on every
  use; `Mailer` (`ConsoleMailer` until `SMTP_PASSWORD` is set) sends rendered mail.
- Field events (`field_scheduled|sent|clicked|reported|cancelled`, payload `{send_id,
  template_id}`) are emitted via `emitEvent`, listed by `GET /me/events?type=field_*`, and already
  reach the client through `AppState.refreshField()` → `fieldCacheFrom()` →
  `_field` / `fieldClicked` ("caught" state, `AttacksSectionView`).
- Routing: `Screen` + `Routes` map to `location.hash` (`#/lesson/<id>/<section>`); `Screen.Reveal`
  at `#/reveal/<token>` is the precedent for a deep link into the app.
- `AppState.getEvents` takes the `field_*` wildcard, so a new `field_gotcha` event type syncs
  through the same path for free.

## Build this

### A. "You have a pending lesson" reminder email

When a practice email was clicked (`clicked_at` set) and not reported or cancelled, send the
learner **one** reminder email, once, after a delay:

- New columns on `field_sends`: `remind_at timestampWithTimeZone`, `reminded_at
  timestampWithTimeZone NULL`. Set `remind_at` at click time (or lazily from `clicked_at + delay`).
- New config: `FIELD_REMIND_DELAY_S` (default 300; it's a hackathon demo — visible effect beats
  realism). Document it in `deploy/.env.example`.
- In the existing scheduler poll: for rows where `sent_at` set, `clicked_at` set, `reported_at`,
  `cancelled_at` and `reminded_at` all null, and `remind_at` due — render the reminder template and
  send it through `Mailer`, set `reminded_at`, and `emitEvent(userId, FIELD_REMINDED,
  fieldPayload(sendId, templateId), now)` with new constant `FIELD_REMINDED = "field_reminded"` in
  `tea/model/Events.kt`. Re-check `reported_at`/`cancelled_at` **inside the transaction**: a report
  arriving after the click must suppress the reminder.
- New template file `content/field/lesson-reminder-01.<lang>.json` (en, fr, es; placeholder copy
  like the existing `practice-email-01.*.json` until a human writes the real one). Extend the
  placeholder set with `{{lesson_url}}` = `APP_PUBLIC_URL` + `#/lesson/<lesson_id>/0` from
  `field_sends.lesson_id`. Copy tone: calm, zero shame — "one practice email is waiting; your
  lesson is pending. Come finish it." The same "Manage ThisIsTea emails" footer as the practice
  email.
- If `lesson_id` is blank or unknown, skip the reminder (log at WARN) rather than sending a broken
  link.

### B. Lesson pending state

While a `field_clicked` event exists for a `lesson_id` whose `field_gotcha` has not yet been seen,
that lesson is **pending again**:

- Extend `FieldCache`/`fieldCacheFrom` (and the per-send correlation it already does via
  `send_id`) to expose pending catch state **per lesson**: pair each `field_clicked` with the send's
  `lesson_id` (add `lesson_id` to the `fieldPayload` emitted at click/report time — it's already on
  the row — and read it back; fall back to the single `FIELD_TEMPLATE_ID` lesson when absent).
- A pending lesson surfaces a warning: on the Home lesson card and inside `LessonProgress`, a
  small pill in `TeaColors.spider` with white text and the 2dp ink outline (same shape as the
  existing chips). Copy like "Caught: lesson pending".

### C. The gotcha intercept (Spider's screen)

When the learner opens a lesson that is pending (from the reminder link, Home "Continue", or
Practice), the lesson's first screen is **intercepted once** before the first question:

- New `Screen.Gotcha(sendId: String, lessonId: String)` routed at `#/gotcha/<sendId>`, added to
  `Screen`, `Routes`, and the App dispatch — mirroring how `Screen.Reveal` was added. Intercept by
  routing `Screen.Lesson` through the gotcha first (e.g. `Screen.Lesson(id, section)` navigates to
  the gotcha when `id` has a pending catch), or by an in-lesson pre-section step — pick whichever
  needs fewer moving parts, but the URL must land back on the lesson after the intercept and back
  navigation must never re-show it.
- **The whole page turns red.** This is the one screen in the app that is not on `paper`: the
  full window, edge to edge, is Spider red. Use a red that is already in our palette, not a new hue:

  | Role | Token | Value | Where it comes from |
  |---|---|---|---|
  | Page background | `TeaColors.badDeep` | `#7A1424` | `--bad-deep` |
  | Glow at the top of the page | `TeaColors.spiderDeep` | `#B0182E` | `--spider-deep` |
  | Cards on the page | new `TeaColors.gotchaCard` | `#4A1425` | `.trap.caught` background in `tea-theme.css` |
  | Card outline, hard shadow, step numbers | `TeaColors.spider` | `#E5384F` | `--spider` |
  | Text | `TeaColors.webInk` / `webSoft` | `#F6E9FF` / `#B9A3CC` | Spider's night palette |
  | Highlighted facts ("you clicked it") | `TeaColors.spiderSoft` on `spider` | `#FFE0E5` | `--spider-soft` |

  - Background: `Brush.verticalGradient(listOf(spiderDeep, badDeep))` over the top ~40% of the
    screen, then flat `badDeep`, so the red feels deep rather than like an error alert. Same in light and dark
    mode (Spider is always dark, like `SpiderPanel`).
  - Add `gotchaCard` to `TeaColors` next to the `web*` tokens, and mirror it in
    `static/tea-theme.css` as `--gotcha-card: #4a1425;` so the CSS reference and Kotlin stay in sync.
    Only Spider-only components may read these raw tokens (rule at the top of `TeaColors.kt`).
  - Reuse the decorative web: make `SpiderWeb` in `SpiderPanel.kt` `internal` and draw it in the
    top-right corner of the page (bigger, ~360dp, white at 0.15 alpha).
  - Put the Spider portrait (`persona_spider_full.png`) at the top, with his name tag in his
    persona font (Cormorant Garamond italic, through `Voice(Persona.SPIDER)`).
  - Wrap the content in `Voice(Persona.SPIDER)` and provide `LocalOnSpiderPanel provides true`
    (as `SpiderPanel` does), so `PersonaBubble`, `SectionHeading` and name tags switch to the night
    palette by themselves. Don't restyle them by hand.
  - Cards: radius 28 (`TeaShapes.panel`), 2dp `spider` outline, 6dp hard shadow in `spider`
    (`hardShadow(TeaColors.spider, …)`), background `gotchaCard`. This is the `.attack-panel` look on
    a red page.
  - Choice buttons: the existing `TeaButton` / `ChoiceRow` look (white surface, ink text, ink hard
    shadow) so they stand out on the red. The main "Continue to the lesson" button keeps the
    turtle `.btn-go` style: green on red is the way out.
  - Top bar: no back arrow during the interview (the learner must answer); the bar sits on the red
    with `webInk` text.
  - Entrance: the lesson screen fades to red (`animateColorAsState` from `TeaTheme.colors.paper`
    to `badDeep`, ~450ms), then the Spider's first bubble appears. One short animation, no shaking
    or flashing.
  - Leaving: on "Continue", fade back to `paper` and open the lesson. The lesson keeps its normal
    look; the red is only for the intercept.
- Spider's lines use `PersonaBubble(Persona.SPIDER, Mood.SMUG, …)`. The practice email and facts
  use `gotchaCard` surfaces; the email heading uses `SectionHeading`.
- The interview, in order, choices via `ChoiceRow`/`TeaButton` (never free text):
  1. **Recognition.** Fetch the caller's clicked send through the authenticated
     `GET /me/field/sends/{sendId}` endpoint. Show its subject and safe rendered text in a
     `gotchaCard`. Spider first says, "I sent you a little something. Take a good look." Then ask
     "Do you know this email?" and "Does it sound familiar? Did it look real to you?" — Yes / No.
  2. **"Yes" branch:** Spider reveals that the learner fell for the practice trap. Show a facts card
     with the click time, email subject, and the fact that the scammer learned the link worked;
     don't claim credentials, forms or data that we don't record. Follow with the four-steps card.
  3. **"No" branch:** Spider notes that the email landed in the learner's inbox and the link was
     clicked at the shown time, then asks why it worked. Offer the localized reason choices (who
     seemed to send it / urgency / a normal-looking request / no alternate verification). After a
     choice, show Spider's mapped reply, the same facts card, then the four-steps card.
  4. **Continue** → `Screen.Lesson(lessonId, 0)`, which now runs normally.
- On completion (or dismiss), persist that the gotcha is done **server-side**: emit a
  `field_gotcha` event for the same `send_id` so the pending state clears on every device (that's
  why it must be an event, not a `LocalState` flag). Treat `field_gotcha` like the other
  `field_*` events: emit it through the same `emitEvent`-equivalent the client uses for
  `practice_item_answered` (follow `AppState`'s event upload path — add a client-side emit for
  this if only server code emits events today; keep the `field_` prefix so `getEvents` picks it up).

### D. Tone and safety (non-negotiable)

- Spider **teases, never shames** (see `docs/characters.md`): smug about his own trick, never "you
  failed". The four-steps card points to what the learner can do next time.
- The reveal facts are honest: only `clicked` + timestamp + which email. If any of that is missing
  (unknown token/send), show the generic reveal, never claim "you clicked".
- Guests (no session) get no reminder and no gotcha; the lesson behaves exactly as today.
- No new event types outside `field_*`; no new tables beyond the two `field_sends` columns.

## Files you'll touch (expected, adjust as needed)

- `server/.../db/Tables.kt` — `remind_at`, `reminded_at` on `FieldSends`
- `server/.../field/FieldScheduler.kt` — reminder pass in the existing poll
- `server/.../field/FieldRoutes.kt` / `FieldTemplates.kt` — `{{lesson_url}}`, optional send fetch
- `server/.../Config.kt`, `deploy/.env.example` — `FIELD_REMIND_DELAY_S`
- `shared/.../model/Events.kt`, `model/Dto.kt` — `FIELD_REMINDED`, `FIELD_GOTCHA`, payload `lesson_id`
- `shared/.../app/AppState.kt`, `app/Routes.kt` — `Screen.Gotcha`, pending-per-lesson in `fieldCacheFrom`
- `shared/.../ui/screens/gotcha/GotchaScreen.kt` — the interview (new file)
- `shared/.../ui/screens/lesson/` — pending warning + intercept into the gotcha
- `shared/.../ui/screens/home/HomeScreen.kt` — pending warning on the lesson card
- `shared/.../composeResources/values{,-es,-fr}/strings_learn.xml` — all `gotcha_*` strings ×3
- `content/field/lesson-reminder-01.{en,fr,es}.json` — reminder template (placeholder copy)

## Tests

Match the existing test style (`server/src/test`, `shared/src/jvmTest`, `runComposeUiTest`):

- Scheduler: clicked, unreported, due → reminder sent once, `field_reminded` emitted; reported or
  cancelled → nothing sent; a report landing between click and poll suppresses it.
- `fieldCacheFrom`: pending state per `lesson_id`; cleared by `field_gotcha` for that `send_id`.
- `Routes`: `#/gotcha/<sendId>` round-trips.
- UI test: both interview branches render; "Continue" reaches the lesson; a second visit skips the
  gotcha.
- Add the gotcha screen to `GalleryScreen` (both branches) so the red page can be reviewed without
  running the whole email loop. Check it at phone width and desktop width, light and dark mode.
- Run `./gradlew -Ptea.android=false :shared:jvmTest :server:test` — all green, plus
  `-Pstrict=true` for the translation check.

## Demo check (manual)

`RATE_LIMITS=off ./gradlew -Ptea.android=false :server:run` +
`./gradlew -Ptea.android=false :composeApp:wasmJsBrowserDevelopmentRun`, register with debug code
`426769`, opt in at the end of lesson 1, click the practice link (ConsoleMailer prints it), watch
the reminder email print after `FIELD_REMIND_DELAY_S`, open the link, and walk the gotcha into the
lesson.
