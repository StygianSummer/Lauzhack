# Practice-email trigger (first loop)

> Status: **proposal** (krishna, 2026-11-21; revised by nathann for honesty, krishna to confirm). Owner: krishna.
> **Satellite file.** What to build for MVP 0 is defined only in [docs/mvp0-plan.md](mvp0-plan.md)
> (§9.6, §10.6, §12, §17). MVP 0 sends **one** practice email per press; the spaced resend series below
> is a **later loop**. If this file disagrees with the plan, **the plan wins**.
> **Stack: Kotlin** (Ktor server endpoint + Compose button), matching [tech-stack.md](tech-stack.md).

## What this is

ThisIsTea is a **cyber security literacy course**. After a lesson, a learner can **ask** for a practice
email in their own inbox, to check that what they learned holds outside the app. Three rules make it
honest, and they are requirements, not options:

- **The learner asks for it, knowingly.** The button says plainly that practice emails are coming.
  Only *when* they arrive and *what they look like* stay secret; that is the test. Nobody else is ever
  sent anything.
- **Nothing is imitated.** Emails come from our own plain address (`thisistea@gmail.com`), use
  fictional names, and never imitate a real company, service or person. No look-alike domains.
- **Nothing is collected.** The link opens a short, blame-free reveal page. There is no login form,
  nothing to type, and we record only whether the link was clicked or the email was reported.

## Where this fits

```
lesson on spotting scams
   → "Want to test yourself for real?" card, learner presses "Yes, send me practice emails"
      → wait a random delay (15s – 1m30s in loop 1)
         → send one practice email from our plain address
            → learner clicks          → reveal page: "This was the practice email you asked for.
            → or ignores it                Here's what gave it away." (records `clicked`)
            → or reports it in the app → reward + "how you caught it" replay (records `reported`)
               → (later loop) schedule the next one, spaced by how they did
               → the result feeds the next lesson
```

**My part:** the opt-in button, the random delay, the send hand-off, the reveal page and the outcome
record. The email text is written by a human with the team; feeding outcomes into lessons is the
lesson owner's piece.

## Why a delay, and why random

- If the email arrived the instant the learner pressed the button, they would connect the two and stay
  on guard. That is "reading the label" (see [onboarding-and-field-mode.md](onboarding-and-field-mode.md)):
  the context gives the answer away.
- A short, unpredictable gap (15s–90s) lets the email land while the learner has moved on. The learner
  knows one is coming, but not when or what it looks like.
- Later loops widen the range (hours, then days) and say so on the button.

## The button

- Card at the end of the lesson: "Want to test yourself for real? We'll send **one practice phishing
  email** to {{email}} at a random moment in the next few minutes. It's harmless and never asks for a
  password. See if you spot it." Buttons: **"Yes, send me one"** and "Not now".
- After the press: the server says whether it was scheduled. If yes: "Done. Keep an eye on your inbox."
  If not: the reason in plain words (paused in Settings, email not verified, already done).
- One press = one scheduled email. Pressing again does not schedule a second one.
- Settings always shows "Practice emails: allowed / paused / stopped" and whether one is pending.

## The trigger

On press:

1. Check the learner has verified their email, accepted the terms, and has not paused or stopped
   practice emails.
2. Pick a random delay `d` uniformly in **[15s, 90s]**: `15 + random() * 75`.
3. Schedule the send for `now + d` (persisted, so it survives a restart). Log the delay at INFO.
4. When due, render the human-written template and hand it to the `Sender`.
5. A pending send can be cancelled (learner pauses or stops, demo reset).

### Contract with the sender

The trigger calls a `Sender` interface when the timer fires. A `PlaceholderSender` logs the payload; the
real one sends from the plain Gmail account. Payload:

```json
{
  "learner_id": "abc123",
  "to_address": "learner@mail.example",
  "template_id": "practice-email-01",
  "loop": 1,
  "scheduled_at": "2026-11-21T14:00:00Z",
  "fired_at": "2026-11-21T14:00:47Z"
}
```

The template is low-emotion (for example a "problem with your account" notice), written and reviewed by
a human, with exactly one practice link and a footer "Manage ThisIsTea emails" linking to Settings.

## The reveal page

- The practice link carries a **one-time token** (`/f/c/<token>`), which records `clicked` and
  redirects to the in-app reveal.
- The reveal is short and blame-free: "This was the ThisIsTea practice email you asked for. We only
  stored that the link was clicked." Then the four steps for this email, the clues that gave it away,
  and "Practise this pattern".
- There is **no form and nothing to type**: no email field, no password field. Research on phishing
  simulations found that pages after a click help little and reporting helps a lot, so the reveal stays
  short and the reward goes to reporting.

### Outcome contract (for the lesson piece)

```json
{ "learnerId": "abc123", "templateId": "practice-email-01",
  "outcome": "clicked", "at": "2026-11-21T14:01:10Z" }
```

`outcome` is `clicked` or `reported`. Stored as `field_*` events in the shared `events` table
(contract C1 in the plan).

## Later loop: spaced practice emails (not MVP 0)

Instead of one email, a learner can opt in to a short **series**, spaced like flashcards. The opt-in
text then says so: "We'll send you a few practice emails over the next days. You can stop any time."

| Grade | Outcome | Next interval (compressed demo values) |
|---|---|---|
| 0 | `clicked` | ~30s: reinforce soon |
| 1 | ignored (no click, no report within the window) | previous × 2.5, capped |
| 2 | `reported` | previous × 2.5, capped, plus a reward |

- **Graduation:** after 2 consecutive emails without a click, stop the series.
- Any click resets the growth to the short interval.
- Every email carries the "Manage ThisIsTea emails" link; pause or stop cancels the series at once.
- Keep the intervals as named constants so they're easy to tune.

## Rules (inherited, non-negotiable)

From [mvp0-plan.md §17](mvp0-plan.md) and [onboarding-and-field-mode.md](onboarding-and-field-mode.md):

- **Opt-in that says what it does:** no press, no email; pause or stop any time.
- **Our own plain address, fictional names:** no look-alike domains, no imitation of any real brand,
  service or person.
- **Nothing collected:** no login form, no typed data, no tracking pixel. Only `clicked` / `reported`.
- **Low-pressure content only:** no threats, no family emergencies.
- **Honesty test:** if a learner would feel tricked when they find out *what* we did, change the design.

## What to build

| Priority | Piece | Owner |
|---|---|---|
| Must | Opt-in card + button with honest copy + server answer shown | krishna |
| Must | Ktor endpoint: checks, random 15–90s delay, persisted scheduled send | krishna |
| Must | `Sender` interface + `PlaceholderSender` that logs the payload | krishna |
| Must | Cancel a pending send (pause/stop, demo reset) | krishna |
| Must | Click endpoint → reveal page (no form) + `clicked` outcome | krishna |
| Must | Report button in the app → `reported` outcome | krishna |
| Later | Spaced series with its own opt-in text | krishna |
| Not mine | The practice email text (human-written, reviewed) | team |
| Not mine | Feeding the outcome back into the next lesson | lesson owner |
