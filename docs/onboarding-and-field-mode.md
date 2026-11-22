# Onboarding, placement test and field mode

> **Satellite file.** What to build for MVP 0 is defined only in [docs/mvp0-plan.md](mvp0-plan.md). This file is the background research and the later field-mode ideas (SMS, calls, NGO campaigns). If this file disagrees with the plan, **the plan wins**.

> Status: **proposal** (nathann, 2026-11-21), pending team agreement (see [decisions.md](decisions.md)).
> Facts marked ⚠ need checking before they go on a slide.

Three linked ideas:

1. **Onboarding questions:** a few questions to personalise the content (channels, life situation, interests).
2. **Placement test:** about 3 minutes of quick judgements that give a starting mastery level per step,
   so a confident learner does not sit through beginner drills and a beginner is not lost.
3. **Field mode:** with explicit opt-in, the app sends realistic practice scams **outside the app**
   (email, SMS, later phone calls). Inside the app, every message is suspicious because "it's the
   scam app". Outside, the learner has to notice it without that hint, like in real life.

The app itself stays Duolingo-style (short daily lessons, streaks, spaced practice of the four steps).
Field mode is the extra piece that tests whether the habit works in the learner's real inbox.

## Why

- **The "read the label" problem.** In a training app, the context gives the answer away: the learner
  knows a trap is coming. Studies of phishing detection find that people who know they are being
  tested score better than in normal life ⚠ (Parsons et al. 2015, *Computers & Security*). A test in
  the real inbox, at a random time, removes that hint. This is the strongest version of our transfer
  test.
- **Personalisation makes practice relevant.** A senior who never shops online gains little from
  parcel scams; an NGO treasurer needs changed-invoice scams first.
- **The placement test respects expertise.** Support that helps novices slows down experts
  (expertise reversal, Kalyuga). Starting everyone at zero wastes their time and makes them quit.
- **Evidence for the design of field mode:** a large Swiss study of phishing simulations in a company
  ⚠ (Lain, Kostiainen & Čapkun, IEEE S&P 2022, ETH Zurich) found that the "training page after you
  click" did **not** help and could even make people more vulnerable, while letting employees **report**
  suspicious messages worked well. So we reward reporting and checking; the page after a click stays
  short and blame-free.

## 1. Onboarding questions

At most 8 questions, one per screen, big buttons, every question skippable. Plain words.

| # | Question | Used for |
|---|---|---|
| 1 | Language and country (pre-filled from the device) | Language, country-specific scams, resource cards |
| 2 | "I'm here for: myself / to help an older relative / my NGO work" | Content pack (seniors, NGO, carer) |
| 3 | "Which do you use?" SMS, email, messenger apps, phone calls, social media | Which channels appear in scenarios and in field mode |
| 4 | "What do you do online?" banking, shopping and parcels, job search, investing, donations, dating/social. NGO: invoices, grants, shared accounts, beneficiary data | Which scam families come first |
| 5 | "Have you ever had a message you weren't sure about?" (optional, free text or choices) | Starting topic; tone (gentle if they were a victim) |
| 6 | "How good are you at spotting scams?" 1–5 | Compared with the placement result: finds overconfidence |
| 7 | "Do you prefer bigger text?" | Accessibility default (do not ask age for this) |
| 8 | Daily goal: 2 / 5 / 10 minutes | Lesson length, reminders |

Data rules: keep only what changes the experience. No real bank names, no family names, no address.
Store the profile locally first; sync only if the user creates an account.

Sketch of the profile (not a contract yet):

```json
{
  "language": "fr", "country": "CH", "pack": "seniors",
  "channels": ["sms", "email", "phone"],
  "interests": ["banking", "parcels"],
  "self_rating": 4, "large_text": true, "daily_minutes": 5,
  "placement": { "step1": 0.6, "step2": 0.8, "step3": 0.4, "step4": 0.2,
                 "false_alarm_rate": 0.1, "overconfident": true },
  "field_mode": { "enabled": false }
}
```

## 2. Placement test

About 10 items, about 3 minutes, right after onboarding. Presented as "let's see where to start",
never as an exam. Items come from the same scenario pool (seed + variations), in the learner's
language and country.

**Item types**

- **Scam or fine?** One message. About half are **legitimate twins**, so answering "scam" to
  everything does not win.
- **Which one is fake?** Two similar messages, emails or news posts side by side. Includes one
  fake-news item ("which headline would you forward?").
- **Tap the warning sign:** tap the part of the message that worries you (sender, link, urgent words,
  the request). Each tap maps to one of the four steps.
- **What would you do next?** Choices such as "call the number in the message" / "call the number on
  my card" / "reply" / "ignore". Tests step 4, the step people miss most.
- After each answer: **"How sure are you?"** (3 levels).

**What we get from it**

- A **starting mastery level per step**, which sets which step prompts start faded (same tracking as
  the main engine, task #4).
- **Hit rate and false-alarm rate** separately (from signal detection theory). Someone who flags
  everything as a scam needs legitimate twins, not more scam examples.
- **Overconfidence:** high self-rating or "very sure" on wrong answers. These learners get field mode
  suggested earlier and more legitimate twins.
- **Short adaptive path:** if the first 4 answers are right and confident, jump to the hard items
  (subtle lookalike sender, a legitimate message that uses urgency).

**Feedback screen:** "You're strong at spotting urgency. Let's work on checking through your own
channel." No score out of 10, no "you failed".

**Re-test** every few weeks with new items from the variation engine. The progress curve is useful
for learners and is an impact number for NGO partners (IMC).

## 3. Field mode (practice outside the app)

### How it works

1. **Opt-in, after the learner knows the app** (for example after 5 lessons), on its own screen,
   per channel: "Can we sometimes send you practice scams by email / SMS / phone? You won't know when.
   You can pause or stop at any time."
2. The learner picks limits: maximum frequency (default: 1 per week), quiet hours, channels.
3. The engine picks a scenario that matches their profile and current weak step, and sends it through
   the real channel from **our own** sender address or number (fictional brand in the content, never
   spoofing).
4. What the learner does decides what happens next:

| Learner action | What happens |
|---|---|
| **Reports it** (forwards to our report address, or "Report" button in the app) | Best outcome: big reward, streak bonus, short "how you caught it" replay through the four steps |
| **Checks through their own channel** (e.g. calls the "official" number of the fictional bank saved in the app, or asks in the app "did you send this?") | Best outcome: same reward; this *is* step 4 |
| **Ignores / deletes** | Fine. Next time they open the app: "Did you notice anything strange this week?" (recall question) |
| **Clicks the link** | Opens a 30-second, blame-free reveal: "This was practice. Here is what gave it away." Then one scenario on that pattern in the app |
| **Replies, types data, presses a key during a call** | Same reveal, straight away. Nothing they typed is sent or stored |

### "Did you send this?" (a check that is not cheating)

The learner can always ask the app whether a message came from us. That is exactly the habit we want
(check through a channel you control). The answer must be careful:

- "Yes, this was practice. Well done for checking." → reward.
- "**No, we did not send this. That does not mean it is safe:** it could be a real scam. Let's go
  through the four steps together." → this links to the "check a real message" feature (task #16).

### Personalising field messages

Use the profile categories (channels, interests, pack, country) and the first name at most. Never
use details that would feel like we are spying (family names, places, real employer). Realism comes
from the **pattern**, not from personal data.

### Safety and ethics rules (non-negotiable)

- **Explicit, informed, per-channel consent**, easy pause and stop, shown again every few weeks.
- **No spoofing and no real brands:** our own domain and number, fictional banks and companies.
  We do not try to get around spam filters; if a filter catches our message, that is fine.
- **Never collect real secrets:** links go straight to the reveal page; no working login forms; phone
  call key presses are not stored.
- **No distressing scripts in the wild for seniors:** no "grandson in hospital", no threats of arrest.
  Emotional scams stay inside the app, where the learner knows it is practice. Outside: lower-emotion
  patterns (parcel fee, bank "security check", fake job, fake invoice).
- **Carer / NGO enrolment:** a relative or an NGO can invite someone, but the learner gives consent
  themselves.
- **The app states its own rule clearly:** "We will never ask you for money, a password or a code,
  not even in an exercise." That stops real scammers from pretending to be us.
- **Data protection:** minimal data, encrypted contact details, deletion on request; Swiss nLPD /
  GDPR ⚠ (check what consent wording is needed).
- **SMS and calls cost money:** frequency cap per user; email first.

### NGO version (later)

An NGO admin can run a small campaign for their own volunteers (for example a changed-invoice email),
with the same rules, and see only aggregated results (who reported, not who clicked). This matches how
companies run phishing simulations, but without blame.

## What to build this weekend

| Priority | Piece | Why |
|---|---|---|
| Must | Onboarding: questions 1–4 and 8 | Cheap, personalises the demo |
| Must | Placement test: 8–10 items, per-step result feeding the fading engine | Strong Scholé story: the app adapts from minute one |
| Stretch | Field mode, **email only**, for team members who opted in | Live demo moment (below) |
| Pitch only | SMS, phone calls, NGO campaigns | Next steps |

**Possible demo moment:** during the pitch, a teammate's real phone receives a practice email.
They don't click; they report it from the app; the screen shows the reward and the four steps they
used. This shows transfer outside the app, not only on a held-out scenario inside it.

## Open questions

- Does the placement test reuse the scenario JSON with a flag, or a separate item format? Any change
  to the format needs a decision entry.
- Which email-sending service fits the "frugal" rule and the hackathon timeline?
- Should field mode wait until a minimum level, so beginners don't fail in the wild and feel bad?
