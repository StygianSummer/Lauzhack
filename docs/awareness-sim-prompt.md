# Build prompt: practice-email trigger (loop 1)

> A ready-to-paste prompt for a coding agent to build krishna's piece of the first loop.
> Design: [awareness-sim-trigger.md](awareness-sim-trigger.md). Keep the two in sync.
> **The plan wins:** [docs/mvp0-plan.md](mvp0-plan.md) §9.6, §10.6, §12, §17.

---

You are building one piece of **ThisIsTea, a cyber security literacy course** (LauzHack hackathon).
Learners take short lessons on spotting scams. At the end of a lesson, a learner can **ask** for a
harmless practice email in their real inbox, to check that the habit holds outside the app. Three rules
must hold in your code:

1. **The learner asks for it, knowingly.** The button says plainly that a practice email is coming. Only
   the moment it arrives and what it looks like stay secret.
2. **Nothing is imitated.** The email comes from our own plain address and uses fictional names. No
   look-alike domains, no real brands, services or people.
3. **Nothing is collected.** The link leads to a short reveal page with no form. We record only whether
   the link was clicked or the email was reported.

## Stack

- **Kotlin.** Server: **Ktor**. UI: **Compose Multiplatform**. Do not introduce another language.
- Sending goes through a `Sender` interface; start with a `PlaceholderSender` that only logs.

## Build this

1. **Compose opt-in card** at the end of the lesson:
   - Text: "Want to test yourself for real? We'll send one practice phishing email to {{email}} at a
     random moment in the next few minutes. It's harmless and never asks for a password. See if you
     spot it."
   - Buttons: **"Yes, send me one"** and "Not now".
   - On "Yes": call the server, disable the button, show the server's answer: scheduled → "Done. Keep an
     eye on your inbox."; not scheduled → the reason in plain words.

2. **Ktor endpoint** `POST /me/readiness` (session auth) with `{ "lesson_id": "email-01" }`:
   - Checks: email verified, terms accepted, practice emails not paused or stopped, no earlier send for
     this lesson. If one fails, return `{ "scheduled": false, "reason": "paused" | "stopped" |
     "not_verified" | "already_done" }`.
   - Otherwise set `practice_emails_allowed = true`, pick `d = 15.0 + Random.nextDouble() * 75.0`
     seconds, persist a `field_sends` row due at `now + d`, log the delay at INFO, and return
     `{ "scheduled": true }`. Never return the send time.

3. **Scheduler**: a coroutine in the application scope that every 5 s picks due, unsent, uncancelled
   rows, renders the human-written template (`content/field/practice-email-01.<lang>.json`; ship a
   placeholder that says "Placeholder practice email: replace before the demo"), and calls
   `sender.send(payload)`. If the learner paused or stopped meanwhile, cancel instead.

4. **`Sender` interface** `suspend fun send(payload: ScheduledSend)`, with `PlaceholderSender` (logs
   only). The real Gmail SMTP sender is swapped in later without touching the trigger.

5. **Click → reveal.** The email's one link is `/f/c/<token>` (random 32-byte token, store only its
   SHA-256). It records `clicked` once and redirects to the in-app reveal: "This was the ThisIsTea
   practice email you asked for. We only stored that the link was clicked." plus the four steps and the
   clues from the template. **No form, no input fields.**

6. **Report.** A "Got a suspicious email?" button in the app calls `POST /me/field/report`, which marks
   the latest unreported send as `reported`. The answer is always careful: "If it came from us, well
   done for checking. If not, it could be a real scam: let's go through the four steps."

7. **Pause / stop / cancel.** `PUT /me/settings {practice_emails: allowed|pause|stop}`; pause and stop
   cancel pending sends. `POST /admin/field/cancel` (admin key) for demo resets.

## Data shape

```kotlin
@Serializable
data class ScheduledSend(
    val learnerId: String,
    val toAddress: String,
    val templateId: String,
    val loop: Int = 1,
    val scheduledAt: String, // ISO-8601
    val firedAt: String? = null,
)

@Serializable
data class OutcomeEvent(
    val learnerId: String,
    val templateId: String,
    val outcome: String, // "clicked" | "reported"
    val at: String,      // ISO-8601
)
```

Outcomes are written as `field_clicked` / `field_reported` events to the shared `events` table.

## Rules (non-negotiable)

- No press, no email. Pause or stop works at any time.
- Plain sender address, fictional names, no look-alike domains, no real brands.
- No login page, no form, no tracking pixel; nothing typed is ever collected.
- Low-emotion template only (no threats, no family emergencies). The text is written by a human.

## Done when

- Pressing "Yes, send me one" schedules a send 15–90 s later, visible in the server log; the delay is
  random across presses; without the press nothing is ever sent.
- The opt-in text and the server answer tell the learner truthfully what will happen.
- Clicking the link records `clicked` and shows the reveal; there is nothing to type anywhere.
- Reporting records `reported`. Pausing before the send cancels it.
- Swapping `PlaceholderSender` for the real sender changes nothing else.
