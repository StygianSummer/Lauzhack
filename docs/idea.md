# The idea

> **Satellite file.** What to build for MVP 0 is defined only in [docs/mvp0-plan.md](mvp0-plan.md). This file is the long-term vision, research basis and content packs (ideas for later). If this file disagrees with the plan, **the plan wins**.

> Status: **proposal**, pending team agreement (see [decisions.md](decisions.md)).

## One-liner

A teaching engine that turns a multi-step procedure into a habit: drills for each step, then full
scenarios where the step prompts fade away as you master them. The first topic is spotting scams,
for seniors and for NGO volunteers.

## Why

- Duolingo-style apps teach small, isolated facts well, but few tools teach **multi-step
  problem solving** that learners can **apply to new situations**.
- Scams are a clear case: people know individual tips ("don't click links") but fail when a
  new scam doesn't look like the examples they were shown.
- NGOs: phishing is the most common attack charities report (UK Cyber Security Breaches Survey).
  About one-third of UK charities reported an incident in 2024 (Fakunle & Asonye, IJCISIM 2026).
  For an NGO with no budget, training people is the cheapest protection, and staff training is on
  the Australian Cyber Security Centre checklist for charities. NGOs (e.g. Pro Senectute) are also the
  **channel that reaches seniors**, so they are our customer and our distributor.

## The procedure we teach (the four steps)

1. **Who is really talking?** Channel, sender, how they got your contact.
2. **What emotion are they pushing?** Urgency, fear, greed, love, authority.
3. **What exactly are they asking for?** Click, pay, a code, a login, or forwarding the message.
4. **How can I check through a channel I control?** Call the number on your bank card, ask the
   relative directly.

## Teaching design

1. **Drills for each step:** short Duolingo-style exercises for each of the four steps.
2. **Whole scenarios in a simulated phone:** messages arrive; the learner taps, replies, calls or
   ignores them. Afterwards, a replay shows what would have happened.
3. **Fading support:** at first each step is prompted. As **mastery per step** rises (simple
   per-step tracking), that step's prompt disappears. Support is removed per learner, not on a fixed
   schedule (expertise reversal effect).
4. **Transfer by design:**
   - Vary the surface, keep the structure: different-looking scams with the same deep pattern,
     mixed together in practice rather than grouped by type.
   - **Compare two cases:** show two different-looking scams and ask what they share
     (Gentner's analogical comparison).
   - **Held-out test:** a scam type never practised (e.g. the Telegram "group moved" worm).
     Catching it unprompted is the demo moment.

Research to cite: 4C/ID (van Merriënboer), faded worked examples (Renkl & Atkinson), cognitive
apprenticeship (Collins, Brown & Newman), expertise reversal effect (Kalyuga), analogical comparison
(Gentner).

## Detailed design docs

- [scenario-engine.md](scenario-engine.md): slots, variation operators, legitimate twins,
  combination grid for the transfer test, shortcut breaker, all seed scenarios (general, NGO, Switzerland).
- [localization.md](localization.md): 9 languages, scams adapted per country, right-to-left,
  dark theme and accessibility, "what to do now" resource cards.
- [onboarding-and-field-mode.md](onboarding-and-field-mode.md): onboarding questions for
  personalisation, a 3-minute placement test (per-step starting level), and opt-in **field mode**
  (practice scams sent by real email/SMS/calls, so the learner can't rely on "it's the scam app").

## Content packs

- **Seniors:** fake bank SMS, fake parcel fee, remote job offer that steals identity or asks for
  money, "double your money" investment, "grandson in trouble", fake news to forward, Telegram
  "group moved" link (held out for the test).
- **NGO staff/volunteers:** fake donor or grant offer, changed invoice bank details, phishing on a
  shared Google/Microsoft account, volunteer forwarding beneficiary data in a group chat.
  Catching an NGO scam after training on senior scams is a second transfer demonstration.
- **Switzerland:** health insurance cold calls (now restricted) and brokers paid by sign-up
  commissions; call centres collecting appointments to sell to brokers; LPP/3a "recovery" and
  "optimisation" services. A third category, **misaligned incentive**: legal or grey, but the advice
  serves the adviser's bonus. Key question: "Who pays this person, and for what?"

## Where AI is used (behind the scenes, never as a chat window)

- Generating **variations** of human-written core scenarios (a human reviews them).
- Playing the scammer within strict limits during a scenario.
- Marking the learner's free-text reasoning against the four steps.
- Optional (AXA): **"check a real message"**. Screenshot → local OCR → simple rules (links, numbers,
  urgency words) → small cheap model fills in the four steps → the user is walked through their
  own message. Build only if the core works by Saturday night.

## Target challenges

| Challenge | Role | What they judge |
|---|---|---|
| Scholé | Main | Clever teaching method; it must teach a newcomer; show the "click" moment; not a chat. |
| IMC | Secondary | Technical quality, social impact for non-profits, presentation. |
| AXA | Optional | A real app, multimodal input, low cost, no AI slop, technical novelty. |

## Out of scope (and why)

- **Full NGO security audit tool:** it becomes a checklist app, not a learning experience.
- **Incident response training (Locked Shields style):** a great future topic for the same engine,
  but too heavy to build and too hard to demo in 48h. Mention it in the pitch as next step.

## Demo script (to refine first, then build only what it needs)

1. Problem (seniors and NGOs, 20s).
2. Drill for one step (15s).
3. A guided scenario, step prompts visible (30s).
4. A later scenario, prompts faded (30s).
5. **Held-out scam caught unprompted: the click moment** (30s).
6. NGO pack and partner channel (20s). Optional: real screenshot check (AXA).

## Possible partners / validation

CyberPeace Institute (Geneva, CyberPeace Builders for NGOs), Pro Senectute (courses for seniors),
Swiss Crime Prevention / S-U-P-E-R.ch (current scam types). Verify names before putting on slides.
Any real reply from them this weekend is worth mentioning on stage.
