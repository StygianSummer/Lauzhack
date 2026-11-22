# AGENTS.md: shared brief for every coding agent on this team

This file is the single source of truth for **all** agents (Claude Code, Codex, Junie/Air,
Copilot, …). Claude Code loads it via `CLAUDE.md`. Read it fully before doing anything.

## The project in one paragraph

LauzHack 2026 (deadline: end of the 48h). We are building a **teaching engine for multi-step
procedures**: learners practise each sub-skill in short drills, then apply the whole procedure in
realistic scenarios, with prompts for each step that gradually disappear as they master them
(based on 4C/ID and faded worked examples). The first topic is **scam defence**, with two content packs:
**seniors** (fake bank, fake job, "grandson in trouble", Telegram "group moved" worm…) and
**NGO staff/volunteers** (fake donor or grant, changed invoice bank details, shared-account phishing,
leaking beneficiary data). The learner acts inside a **simulated phone**, not a chat window.
The demo moment: the learner catches a scam type they never practised, unprompted.
Full design: [docs/idea.md](docs/idea.md).

Target challenges: **Scholé** (main), **IMC** (secondary), **AXA** (optional, only if the
"check a real message" screenshot feature gets built). The main ranking is automatic.

## Before you start any session (mandatory)

1. Confirm you are on your human's branch (see Git rules), then:
   `git fetch origin && git merge origin/main`
2. Read: this file → **[docs/mvp0-plan.md](docs/mvp0-plan.md) (the single source of truth for what
   to build; every other doc is a satellite and the plan wins on conflict)** →
   [docs/decisions.md](docs/decisions.md) → [TASKS.md](TASKS.md) → every file in [docs/handoff/](docs/handoff/).
3. Look for questions addressed to your human in other people's handoff files and answer them.

## Git rules: one branch per person, never `main`

- **Each teammate works only on their own branch, named after their first name in lowercase**
  (e.g. `nathann`). Ask your human for their name if you don't know it.
  - First time: `git fetch origin && git switch -c <name> origin/main && git push -u origin <name>`
  - Later sessions: `git switch <name>`
- **Never commit or push to `main` directly.** Never push to someone else's branch.
  Never force-push a shared branch.
- Get updates from others by merging `origin/main` into your branch, often (at least each session).
- Work reaches `main` only through a **pull request reviewed by another teammate** (human, or their
  agent with the human approving). Keep PRs small and focused, ideally one task each.
- Commit often with clear messages. Push your branch at the end of every session so others can see it.
- If a merge conflict touches someone else's area, stop and ask your human instead of guessing.

## File ownership (to avoid conflicts)

| Path | Rule |
|---|---|
| `docs/handoff/<name>.md` | Only that person's agent edits it. |
| `docs/decisions.md` | Append-only. Add entries at the bottom, never rewrite old ones. |
| `TASKS.md` | Edit only the line of the task you claim or finish. |
| `AGENTS.md`, `docs/idea.md` | Change only in a dedicated PR that a human approved. |
| `content/` | Owned by the content pack owners; the format is a contract (see below). |
| Code folders | Owned by the area owner in TASKS.md; coordinate before editing someone else's. |

## How agents communicate (no direct messaging between machines)

- **Status:** at the end of every session, update your `docs/handoff/<name>.md`
  (done / in progress / blocked / next). Copy [docs/handoff/_template.md](docs/handoff/_template.md) if it doesn't exist.
- **Questions to another person:** add them under "Questions for others" in *your* handoff file,
  starting the line with `To <name>:`. The other agent answers in *their* file under "Answers".
- **Decisions** that change scope, design, or a shared format go to `docs/decisions.md`
  (date · who · decision · why). A human must agree first; agents propose, humans decide.
- **Tasks:** claim a task in [TASKS.md](TASKS.md) by putting your name on it before starting.

## Shared contract: scenario format

Content and engine meet at the scenario JSON format. The draft example is
[content/scenarios/_example.json](content/scenarios/_example.json). Changing its shape needs a
decision entry, because content, engine and phone UI all depend on it.

## Quality and safety rules

- **The four steps** (shared by all content): 1) Who is really talking? 2) What emotion are they
  pushing? 3) What exactly are they asking for? 4) How can I check through a channel I control?
- **Core scenarios are written by humans.** AI may generate *variations*, which a human reviews.
  AXA deducts points for "AI slop".
- **No real brands or people** in the simulated phone or scenarios: use a generic messenger UI
  and fictional banks, companies and names. Describe scam patterns, never build working scam material.
- **Frugal AI:** prefer rules, caching and small models; use a large model only where needed. Keep cost low.
- **Never commit secrets.** API keys (e.g. the OpenRouter key) go in `.env`, which is gitignored.
- The learner must never feel they are just chatting with an AI (Scholé rule).
