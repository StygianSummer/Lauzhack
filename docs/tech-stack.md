# Tech stack and platform split

> **Satellite file.** What to build for MVP 0 is defined only in [docs/mvp0-plan.md](mvp0-plan.md). This file is the longer-term platform split (simulated phone on web/desktop, iOS); MVP 0 has no simulator. If this file disagrees with the plan, **the plan wins**.

Status: **requirement** set by nathann on 2026-11-21 (see [decisions.md](decisions.md)).

## Requirement: Kotlin

The app **must be written in Kotlin**, using **Kotlin Multiplatform (KMP)** for shared code and
**Compose Multiplatform** for the UI. No separate JS/TS front end.

Why:
- One codebase for web, desktop, Android and iOS.
- Dark/light theme and localization count for the JetBrains challenge only with KMP
  (see [localization.md](localization.md)).
- Compose is well suited to drawing the simulated phone.

## Targets

| Target | Kotlin target | Role | Priority this weekend |
|---|---|---|---|
| Web | `wasmJs` (Compose for Web) | Full experience: simulator + drills. **Demo target.** | Must |
| Desktop (Windows/Mac/Linux) | `jvm` | Same as web. Fast dev loop (hot reload). | Must (for development) |
| Android | `android` | Drills only (Duolingo-style theory modules) | Should |
| iOS | `ios*` | Drills only. Needs a Mac with Xcode to build. | Stretch |

## Requirement: different behaviour per platform

The phone app must **not** show the simulated phone. A fake phone frame inside a real phone makes no
sense.

- **Web and desktop:** the simulator (phone mock-up, scenarios, consequence replay) **and** the drills.
- **Phone (Android/iOS):** only the **drills** (short Duolingo-style lessons on the four steps).

How to build it:
1. **What each build contains:** use `expect`/`actual` in the platform source sets. `androidMain`/`iosMain`
   provide a drills-only `AppShell`; `wasmJsMain`/`jvmMain` provide simulator + drills.
   The phone build does not include the simulator.
2. **How it is laid out:** use window size classes (`currentWindowAdaptiveInfo()`). The web app can
   be opened in a phone browser: on a compact width it must show the drills (or a "use a bigger
   screen for scenarios" message), never a phone inside a phone.

## Code layout

```
shared/src/
  commonMain/    scenario engine, content loading (kotlinx.serialization), mastery/progress,
                 the four steps, shared widgets (message bubble, hint card, quiz card),
                 i18n, theme
    drills/      Duolingo-style modules            → every platform
    simulator/   phone frame + scenario player     → web/desktop only
  wasmJsMain/    AppShell = simulator + drills
  jvmMain/       AppShell = simulator + drills
  androidMain/   AppShell = drills
  iosMain/       AppShell = drills
```

The scenario JSON format ([content/scenarios/_example.json](../content/scenarios/_example.json))
stays the contract. It is parsed in `commonMain` and shared by every platform.

## Known risks (test early)

- **Compose for Web renders to a `<canvas>`**: screen readers, text selection and browser zoom
  are weaker than in HTML. Large text and high contrast must be built into our own theme.
- **Fonts must be bundled** for the canvas: Urdu (Nastaliq, right-to-left), Devanagari (Hindi),
  Telugu, emoji. Test right-to-left Urdu on day 1 (TASKS #22).
- **Wasm needs a recent browser** (WasmGC support), and the first download is heavier than a
  typical web page. Check it on an older phone browser.
- **Gradle builds are slow**: develop on the desktop target, build for web when needed.
- **Progress across devices** (drills on the phone counting in the web simulator) needs a backend
  or an account. Out of scope this weekend unless time allows.

## Tooling

- IntelliJ IDEA or Android Studio with the Kotlin Multiplatform plugin.
- Start from the JetBrains KMP wizard (Compose Multiplatform template, targets: web, desktop,
  Android, iOS).
