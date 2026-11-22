# ThisIsTea — Design Guide Rules

> **Satellite file.** What to build for MVP 0 is defined only in [docs/mvp0-plan.md](docs/mvp0-plan.md) (§8 ports these rules to Compose). This file and `static/` are the **design reference only**; the HTML is not shipped and the app is named **ThisIsTea**. If this file disagrees with the plan, **the plan wins**.

Audience: designers and coding agents building or editing lesson pages.
Source of truth: [static/tea-theme.css](static/tea-theme.css) (tokens + components) and the reference page [static/lesson-01-phishing.html](static/lesson-01-phishing.html).
If this file and the CSS disagree, the CSS wins. Then fix this file.

Keywords: MUST / MUST NOT are hard rules. SHOULD is the default unless there is a reason.

---

## 1. What the product is

"ThisIsTea" is a cyber security literacy course. Each lesson is one static HTML page served from `/static` (Ktor). A lesson teaches through four animal characters, each with a fixed job, colour, typeface and bubble shape. The look is **neo-brutalist and playful**: thick dark outlines, hard offset shadows (no blur), flat colour, rounded corners, slightly tilted cards.

Page tone: second person, short sentences, warm, never preachy. Characters have personality; the narrator (the app itself) is neutral.

## 2. File structure

| File | Role |
|---|---|
| `static/tea-theme.css` | The only stylesheet. Shared by every lesson. |
| `static/lesson-NN-<topic>.html` | One page per lesson. Inline `<script>` at the bottom. |

- Every lesson page MUST link `tea-theme.css` and nothing else for styling.
- Do not add per-lesson CSS files. If a lesson needs a new component, add it to `tea-theme.css` under section 6 (Components).
- Inline `style=""` is used in the reference page for a few one-off margins. SHOULD NOT add more; prefer a class or a token (`--s-*`).
- Fonts load through `@import` at the top of the CSS. Pages add `preconnect` hints to `fonts.googleapis.com` and `fonts.gstatic.com` in `<head>`.
- No frameworks, no build step, no external JS. Vanilla JS only.

## 3. Page anatomy (in order)

Every lesson page has the same skeleton. Keep the order.

1. **`.topbar`** (sticky): brand "ThisIsTea" + `ol.steps` progress nav with five anchors: `#content`, `#tools`, `#attacks`, `#status`, `#answers`. JS marks links `done` / `current` from scroll position.
2. **`header.hero#top`**: left = lesson pill, `h1`, subtitle, primary button + meta (time, question count). Right = `.guides`, four tilted character cards (Cat, Turtle, Dog, Spider) each with one teaser line.
3. **`main.wrap`** containing five `section.lesson-section`, each starting with `.section-head` (numbered circle `.step-no` + `h2`):
   1. **Content** (`#content`): two `.lesson-card`s side by side in `.grid-2` — **Cat** (personal) and **Turtle** (organisational). Each = `.top` (char tag, `.char-title`, `.say`), `ol.rules` (3 rules), `.sign-off`.
   2. **Tools** (`#tools`, `.voice-dog`): Dog intro in a `.speaker`, then `.tools-list` of `.tool` rows (icon, name, one-line `.what`, Dog's `.dog-says`, external link button).
   3. **Attacks** (`#attacks`): dark `.attack-panel.voice-spider` (trap banner, interactive email hunt, flag buttons, domain diff), then a `.practice` card (Cat, safe/phish spot-check), then a `.recovery` card (Turtle checklist).
   4. **Status** (`#status`): one white `.card` with a 3-way confidence picker (`.choice-row` of `.btn-choice`). A different character replies per level.
   5. **Answers** (`#answers`): the quiz form. `.quiz-card` per question, a `.score` banner, `.quiz-actions`.
4. **`footer.site-footer`**: one closing line in Spider's voice.
5. Floating UI: `dialog.card-dialog` (enlarged lesson card) and `aside.buddy` (corner guide that pops up and types a line per section) plus a `.sr-only` live region.

Section numbers in `.step-no` MUST match the order in the topbar.

## 4. Characters and voices (the core rule)

> **The narrator speaks in Bricolage Grotesque (headings) + Figtree (body).**
> **A character's font appears ONLY on things that character owns: their name tag, their card title, and what they say.**

Never set a character font on neutral UI (buttons, labels, nav, form text, section headings).

| Character | Job / angle | Accent | Deep (text on white) | Soft (tint) | Font | Bubble radius | Personality |
|---|---|---|---|---|---|---|---|
| 🐱 **Cat** | Personal security, "you first" | `--cat` #f5a524 | `--cat-deep` #9a5b00 | `--cat-soft` #fff1d6 | Space Grotesk 500 | `4px 16px 16px 16px` (one sharp corner) | Sharp, factual, dry |
| 🐢 **Turtle** | Organisational / team security | `--turtle` #1fa97a | `--turtle-deep` #0c6b4b | `--turtle-soft` #d9f5ea | Fraunces 400 (titles 800) | `28px` (fully round, a shell) | Warm, wise, calls the reader "dear" |
| 🐶 **Dog** | Free tools | `--dog` #3b82f6 | `--dog-deep` #1d4fb8 | `--dog-soft` #dbe8ff | Fredoka 500 | `20px 20px 20px 6px` (tail bottom-left) | Eager, friendly, exclamation marks |
| 🕷️ **Spider** | The attacker / trickster | `--spider` #e5384f | `--spider-deep` #b0182e | `--spider-soft` #ffe0e5 | Cormorant Garamond 600 *italic*, larger (1.3rem) | `2px 22px 2px 22px` (asymmetric) | Elegant, menacing, teasing, speaks in ellipses |

### How to apply a voice

- Put `.voice-<name>` (or `data-voice="<name>"` when set from JS) on the **container**. Everything inside inherits `--v-font`, `--v-weight`, `--v-style`, `--v-size`, `--v-accent`, `--v-deep`, `--v-soft`, `--v-on`, `--v-radius`.
- Never hardcode a character's colour or font in a component. Read the `--v-*` variables so the component works for any character.
- Components that consume `--v-*`: `.char-tag`, `.say`, `.voice-line`, `.char-title`, `.rules li::before`, `.buddy`, `.feedback`, `.score`, `.lesson-card .top`.
- Voice building blocks:
  - `.char-tag` → `.avatar` (emoji in a coloured circle; `.sm` for small) + `.name` + `.role`.
  - `.say` → speech bubble. Modifiers: `.on-soft` (tinted bg), `.plain` (no bubble).
  - `.voice-line` → inline character text inside narrator copy (no bubble).
  - `.char-title` → a heading the character owns.
  - `.speaker` → tag + bubble side by side.
- Avatars are **emoji** (single source of truth is the `CHARACTERS` object in the page script). Always `aria-hidden="true"`.

### Character-to-section ownership

| Section | Owner |
|---|---|
| Content | Cat (idea 1), Turtle (idea 2) |
| Tools | Dog only |
| Attacks (hunt) | Spider only |
| Spot-check practice | Cat |
| Recovery plan | Turtle |
| Status feedback | LOW → Turtle, MEDIUM → Cat, HIGH → Spider |
| Quiz feedback | The question's own `voice` |
| Footer | Spider |

### Spider is always dark

Spider speaks from the dark side, wherever he appears: night plum background `--web-bg` #1b1026, cards `--web-card` #2a1a38, lines `--web-line` #4a3560, text `--web-ink` #f6e9ff, muted `--web-soft` #b9a3cc. Any `.say` under `.voice-spider` / `data-voice="spider"` is automatically dark. Do not put Spider on a light bubble, and do not use the dark palette for other characters.

## 5. Colour

Use tokens from `:root`. MUST NOT introduce raw hex values in components unless it is a one-off tint of an existing token.

| Token | Value | Use |
|---|---|---|
| `--paper` | #eef2f9 | Page background |
| `--card` | #ffffff | Cards, bubbles, buttons |
| `--ink` | #111b3a | Body text, outlines, shadows, active states |
| `--ink-soft` | #4b5775 | Secondary text |
| `--line` | #111b3a | Border colour |
| `--rule` | #c7d0e4 | Dividers, idle option borders |
| `--highlight` | #ffe66d | "Look here" marker (found trick, review target, link hover) |
| `--ok` / `--ok-deep` | = turtle / turtle-deep | Correct, done |
| `--bad` / `--bad-deep` | = spider / #7a1424 | Wrong, warning |

Rules:
- Correct = green (Turtle family). Wrong = red (Spider family). Hint = amber (Cat family). "Look here" = yellow `--highlight`.
- Text coloured with a character colour on a white/light surface MUST use the `-deep` variant (contrast). The bright accent is for fills and borders only.
- Text on a character accent fill uses `--v-on` (ink for Cat, white for the rest).
- Meaning is never colour alone: pair with a word (`Correct`, `Not quite`), an icon, a strikethrough (wrong option) or a label.
- Focus ring: `3px solid var(--dog)`, offset 3px. Do not remove it.

## 6. Typography

### Families (tokens)

| Token | Stack | Used for |
|---|---|---|
| `--font-display` | Bricolage Grotesque (600/800) | `h1`–`h4`, brand, step numbers, score number |
| `--font-body` | Figtree (400–700) | Body, buttons, labels, nav, narrator UI |
| `--font-cat` | Space Grotesk | Cat only |
| `--font-turtle` | Fraunces | Turtle only |
| `--font-dog` | Fredoka | Dog only |
| `--font-spider` | Cormorant Garamond | Spider only |
| `--font-mono` | system monospace | Domains and URLs only (`.domain-row`, `.opt-mono`) |

### Scale (1rem = 16px)

| Token | Size | Use |
|---|---|---|
| `--t-xs` | .82rem | Fine print, toggles |
| `--t-sm` | .92rem | Roles, captions, verdict labels |
| `--t-md` | 1.05rem | **Body** (line-height 1.6) |
| `--t-lg` | 1.3rem | Quiz question titles |
| `--t-xl` | 1.6rem | `.char-title` |
| `--t-2xl` | clamp(1.8rem, 4vw, 2.5rem) | Section `h2` (weight 800) |
| `--t-3xl` | clamp(2.5rem, 6vw, 4.3rem) | Page `h1` (weight 800) |

- Headings: Bricolage, `line-height: 1.1`, `letter-spacing: -0.015em`.
- Character speech sizes are set via `--v-size` (Cat 1.02, Turtle 1.08, Dog 1.06, Spider 1.3). Cormorant runs small, hence Spider's bump. Do not "fix" it.
- Spider's italic never applies to his `.name` (name tags stay upright).
- Use `<b>` inside `.rules li` for the rule's headline; the CSS makes it a block in the character font.

## 7. Shape, borders, shadows, spacing

- **Outline**: every card, button, avatar and bubble has `--border` = `2px solid var(--line)`. Dividers inside cards use `2px dashed var(--rule)`.
- **Shadow**: hard offset, zero blur. `--stick` = `4px 4px 0 var(--ink)`; `--stick-sm` = `3px 3px 0 var(--ink)`. Hover lifts (`translate(-1px,-1px)` + bigger shadow); press sinks (`translate(4px,4px)` + no shadow). The attack panel uses a coloured shadow `6px 6px 0 var(--spider)`.
- **Radius**: `--r-lg` 22px (cards), `--r-md` 14px (buttons, rows, inputs), `--r-sm` 10px, pills 999px, avatars 50%. Attack panel 28px. Bubbles use `--v-radius`.
- **Spacing**: `--s-1` .5rem, `--s-2` 1rem, `--s-3` 1.5rem, `--s-4` 2.25rem, `--s-5` 3.5rem. Sections use `--s-5` top padding.
- **Layout**: `.wrap` = max-width 1040px, centred, 1.25rem side padding. `.grid-2` = `auto-fit, minmax(300px, 1fr)`.
- **Tilt**: only the four hero `.guide` cards are rotated (±1–1.5°). Do not rotate anything else.
- No gradients or soft shadows, except the decorative web in `.attack-panel::before` and the `backdrop-filter` blur on the topbar and dialog backdrop.

## 8. Components cheat-sheet

| Component | Class | Notes |
|---|---|---|
| Primary button | `.btn.btn-go` | Turtle green, dark text |
| Default button | `.btn` (`.btn-sm` smaller) | White, outlined, hard shadow |
| Quiet toggle | `.demo-toggle` | Dashed pill, only inside the dark panel |
| Card | `.card` | White, outlined, `overflow: hidden` |
| Lesson card | `.card.lesson-card.voice-*` | Tinted `.top`, numbered `.rules`, `.sign-off`; click opens enlarged dialog |
| Tool row | `.tool` | 3-col grid: icon / text / button (stacks <640px) |
| Email mock | `.email` > `.email-bar`, `.email-head`, `.email-body`, `.fake-link`, `.url-under` | Black 2px outline, white body, fake blue link, `tabindex="-1"` |
| Chat / SMS mock | `.chat-mock`, `.sms-mock` | Quiz "artifacts" shown as they would really look |
| Trap banner | `.trap.caught` / `.trap.dodged` | Spider's reveal; red-tinted vs green-tinted dark |
| Red-flag button | `.flag-btn` | Dark; `.is-locked` (dashed) until found; `aria-pressed` toggles highlight |
| Domain diff | `.domain-diff` > `.domain-row.real` + `.diff` | Mono, differing chars in red |
| Choice picker | `.choice-row` > `.btn-choice` / `.btn-active` | Active = ink fill, pressed in. `.meter` shows 33/66/100% |
| Quiz card | `fieldset.card.quiz-card` | `.q-title` + `.q-no`, optional `.q-artifact`, `.option-row`s, `.feedback` |
| Option row | `.option-row` (`.is-right`, `.is-wrong`, `.opt-mono`) | Wrong = red + strikethrough; right = green |
| Feedback | `.feedback.is-ok / is-hint / is-bad` | Character avatar + bubble, starts with a `.verdict` label |
| Score banner | `.score` | Big `n / total` + character reaction |
| Checklist | `.checklist` | Checkbox rows, tick state persists in `localStorage` |
| Review link | `.review-link` | Pill that sends the learner back to the relevant card/section |
| Corner guide | `.buddy` | Fixed bottom-right; types the line, avatar bobs while talking |
| Dialog | `dialog.card-dialog` | Clone of a lesson card at larger scale (`.is-big`) |

## 9. Interaction and learning-design rules

These are product decisions, not decoration. Preserve them when restyling.

- **Active before passive**: the Spider hunt makes the learner find the three tricks (address, urgency, credentials) before they are explained. Decoy elements (`data-decoy`) cost a "wrong guess"; "Show me the rest" gives up.
- **No answer on the first miss**: a wrong quiz answer shows a hint and allows one retry; only the second miss reveals the answer. Single-choice wrong picks are struck out and disabled. Option order is shuffled each render.
- **Each wrong answer links back** to the card/rule or section that teaches it (`review: { card, rule }` or `{ href }`).
- **Two outcomes for the practice email**: only for a learner who asked for a practice email, Spider reveals whether they clicked its link (`caught`) or not (`dodged`). It is click-only: nothing typed is ever collected, and the copy says so. Without a practice email, the banner is not shown. Copy, button label, link text and recovery title all switch. The "Demo toggle" button is a dev aid; it MUST be removed or hidden once real state comes from the backend.
- **Status** is self-assessed (LOW / MEDIUM / HIGH). Replies are intentionally in character, including Spider's "confident people are my favourite".
- **Persistence**: only the recovery checklist uses `localStorage` (`tea.phishing.recovery`). Wrap storage in try/catch. Quiz answers live in the page script, so a learner can read them; real grading belongs on the server (see the comment above `QUESTIONS`).
- **Quiz copy**: each question has `text`, optional `artifact` (HTML), `options[{t, ok}]`, `hint`, `explain`, `review`, `voice`, and `type: 'single' | 'multi'`. Set `mono: true` when options are URLs.

## 10. Accessibility (non-negotiable)

- Interactive non-button elements get `tabindex="0"`, `role="button"` and Enter/Space handlers (see the email hunt).
- Dynamic text is announced: `aria-live="polite"` on counters, feedback, hunt messages; `role="status"` on trap and score; `role="alert"` on validation warnings.
- Emoji avatars and decorative icons are `aria-hidden="true"`. Icon-only buttons have `aria-label`.
- The corner guide mirrors its line into a `.sr-only` live region; its typed text is `aria-hidden`.
- Honour `prefers-reduced-motion`: the CSS disables all animation and transitions; JS skips the typewriter and shows the full line.
- Fake links inside mock emails are `tabindex="-1"` so they never become real controls.
- `<dialog>` for modals (Esc closes natively; backdrop click closes).
- Keep the `[hidden] { display: none !important }` rule. Toggle visibility with the `hidden` attribute, not inline `display`.
- Maintain the 3px focus ring and `scroll-padding-top: 5.5rem` so anchors clear the sticky topbar.

## 11. Responsive

Breakpoints used: **860px** (hero and attack grids collapse to one column), **820px** (brand hidden, step labels smaller, tighter attack panel), **640px** (tool rows and choice row stack, guide roles hidden, smaller avatars, buddy shrinks).
Design mobile-first thinking, but extend the existing breakpoints rather than adding new ones.

## 12. Copywriting conventions (observed in the reference page)

- Language: English, **British spelling** (favourite, neighbours, finalising).
- **No em dashes.** Use commas, full stops or ellipses (`...`, which is Spider's signature).
- Short sentences. Second person ("you"). Concrete examples (`thisistea.example` vs `thisistae-app.example`).
- Each character speaks in their own register (see table in section 4). Turtle says "dear"; Dog uses "!" and "buddy"; Cat is terse; Spider teases.
- Rules are headline-then-explanation: `<b>Short imperative.</b>One or two sentences of why.`
- Buttons: verb-first, sentence case ("Start the lesson", "Check my answers"). Dog's tool buttons name the tool ("Open Bitwarden").
- Fake domain in all examples: **thisistea.example** (real) vs lookalikes, all on `.example`; fictional brands only (TeamChat, CloudDrive, SwissAlpBank), never real ones. Persona: "Sam".
- Headline numbers stay consistent with the page ("6 questions", "10 minutes", "3 tricks", "3 rules", "5-step plan"). If you change a count, change it everywhere (intro, hero meta, buddy lines, `HUNT_TOTAL`).

## 13. Checklist for a new lesson page

1. Copy the skeleton from `lesson-01-phishing.html`; keep the five-section order and topbar anchors.
2. Link only `tea-theme.css`. Reuse components; add new ones to the CSS, not inline.
3. Assign each section to a character per the ownership table; apply `.voice-*` on the container.
4. Use tokens for every colour, size, radius, shadow and spacing value.
5. Check: character fonts appear only on character-owned elements; `-deep` colours for text on white; outlines + hard shadows on every raised surface.
6. Wire the four scripts: scroll-driven topbar progress, corner-guide `SECTION_LINES`, `CHARACTERS` map, `QUESTIONS` array with hint + explain + review for each.
7. Verify keyboard use, reduced motion, 400px width, and that wrong-answer review links open the right card.
8. Remove dev aids (demo toggle) before shipping.

## 14. Do / Don't summary

**Do**
- Use `--v-*` variables so components stay character-agnostic.
- Keep outlines 2px, shadows hard, corners rounded.
- Pair colour with text or shape for meaning.
- Keep Spider dark and italic; keep Turtle round; keep Cat angular.

**Don't**
- Don't use a character font for navigation, buttons or section titles.
- Don't add blur shadows, gradients, or new accent colours.
- Don't put raw hex values or pixel sizes in components that a token covers.
- Don't reveal quiz answers on the first miss.
- Don't use em dashes or American spellings in copy.
- Don't tilt or animate anything beyond what exists without a reason.
