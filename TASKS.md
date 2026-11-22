# Task board

Claim a task by putting your name in the Owner column **before** starting, then commit that
change on your branch. Status: `todo` · `doing` · `review` (PR open) · `done`.
Areas: engine, phone-ui, content-seniors, content-ngo, content-ch, ai-pipeline, localization, resources, pitch.

| # | Task | Area | Owner | Status |
|---|---|---|---|---|
| 1 | Team agrees on the idea and target challenges (update decisions.md) | pitch | all | todo |
| 2 | Scaffold the Kotlin Multiplatform + Compose project (stack decided: [docs/tech-stack.md](docs/tech-stack.md)); pick hosting for the web build | engine | nathann | review |
| 3 | Finalise the scenario JSON format (`content/scenarios/_example.json`) | engine | | todo |
| 4 | Engine: per-step mastery tracking and fading of step prompts | engine | nathann | review |
| 5 | Drills for each of the four steps | engine | | todo |
| 6 | Simulated phone UI (generic messenger, no real brands) + replay of consequences | phone-ui | | todo |
| 7 | Write 6–8 core senior scenarios + 1 held-out test (Telegram "group moved") | content-seniors | | todo |
| 8 | Write 4–6 NGO volunteer scenarios | content-ngo | | todo |
| 9 | "Compare two cases" exercise | engine | | todo |
| 10 | AI: generate variations of core scenarios (reviewed by a human) | ai-pipeline | | todo |
| 11 | AI: mark learner's free-text reasoning against the four steps | ai-pipeline | | todo |
| 12 | Pitch Scholé mentors early; ask them to play confused learners | pitch | | todo |
| 13 | Get the OpenRouter key at the AXA booth (store in `.env`, never commit) | pitch | | todo |
| 14 | Contact CyberPeace Institute / Pro Senectute for quick validation | pitch | | todo |
| 15 | Demo script + slides (Scholé "click" moment, IMC impact story) | pitch | | todo |
| 16 | Optional (AXA): screenshot → OCR → rules → small model → four-step walkthrough | ai-pipeline | | todo |
| 17 | Variation engine: slots, operators, selection logic ([docs/scenario-engine.md](docs/scenario-engine.md)) | engine | | todo |
| 18 | "Shortcut breaker": track which surface features drive the learner's answers, pick counter-examples | engine | nathann | review |
| 19 | Write legitimate twins for the main seeds | content-seniors | nathann | review |
| 20 | Swiss pack: health insurance cold call, broker visit, LPP recovery/withdrawal, 3a policy (CH1–CH6); check every fact marked ⚠ | content-ch | | todo |
| 21 | Interface translations (`i18n/<lang>.json`): each native speaker claims a language (fr, en, hi, te, ur, de, it, es, ca) | localization | | todo |
| 22 | Right-to-left layout test with Urdu; Devanagari/Telugu fonts | localization | | todo |
| 23 | Dark/light theme + large text + high contrast | phone-ui | nathann | review |
| 24 | Check every link in `content/resources/resources.json`, set `verified: true` | resources | | todo |
| 25 | "What to do now" cards: pick resources by ask × country | engine | nathann | review |
| 26 | AI & sensitive data pack (AI1–AI6) + simulated chatbot screen with "Send / edit / other way"; check facts marked ⚠ | content-ai | ferran? | todo |
| 27 | Onboarding questions (language/country, role, channels, interests, daily goal) → learner profile ([docs/onboarding-and-field-mode.md](docs/onboarding-and-field-mode.md)) | engine | nathann | review |
| 28 | Placement test: 8–10 items (scam or fine, which is fake, tap the warning sign, what next, "how sure?") → per-step starting mastery | engine | nathann | review |
| 29 | Write placement items incl. legitimate twins and one fake-news pair | content-seniors | nathann | review |
| 30 | Stretch: field mode, email only, opt-in team members; report button + "did you send this?" check + reveal page | engine | nathann | review |
| 31 | Gotcha intercept: reminder, pending lesson, authenticated event flow, localized Spider interview and gallery | engine | ferran | review |
