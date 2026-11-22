# Localization, accessibility and resources

> **Satellite file.** What to build for MVP 0 is defined only in [docs/mvp0-plan.md](mvp0-plan.md). This file covers languages and resource cards beyond MVP 0 (which ships en/fr/es only). If this file disagrees with the plan, **the plan wins**.

> Status: **proposal** (brainstorm 2026-11-21).

## Languages

Team languages: French, English, Hindi, Telugu, Urdu, German, Italian, Spanish, Catalan.

- **Interface text:** one translation file per language (`i18n/<lang>.json`), reviewed by a
  native speaker on the team. Cheap for all 9.
- **Scenarios:** don't translate word for word. The AI writes each scenario **directly in the target
  language from the slots**, swapping in scams common in that country:
  - India: fake "KYC update" SMS, electricity-cut threats, "digital arrest" video-call scam.
  - Switzerland: parcel fees, fake police, health insurance cold calls, LPP "recovery" services.
  - Spain: fake tax-office (Hacienda) refunds.
  The structure and answer key stay the same; only the costume changes.
- **Native speakers review** generated scenarios in their language.
- **Scope suggestion:** interface in all 9 languages; fully worked scenarios in 3–4 (French, English,
  one right-to-left language such as Urdu, plus German or Italian for the Swiss angle).

### Technical traps
- **Urdu is right-to-left:** `dir="rtl"` and a Nastaliq font. Test it early.
- **Hindi / Telugu:** proper Devanagari and Telugu fonts; longer text can break layouts.
- Keep country separate from language: a Hindi speaker in Lausanne needs **Swiss** reporting
  resources in **Hindi**.

## Theme and accessibility
- Dark and light theme using colour variables.
- **Large text and high-contrast mode** for seniors. More valuable to IMC judges than dark mode, and about the same cost.
- Note: dark/light mode and localization are bonus points in the **JetBrains** challenge only if we
  build with Kotlin Multiplatform; in a web app they're good for users but don't earn points on their own.

## Resources: show them when needed, not as a list of links

When a learner fails a scenario (or uses the screenshot checker), show a **"what to do now"**
card chosen by **the ask that was given away × the user's country**. Data:
[content/resources/resources.json](../content/resources/resources.json).

| What was given away | First move (urgency matters) |
|---|---|
| Password / login | Change it now; turn on two-factor login |
| Money / card details | Call your bank **immediately** (time decides if money can be recovered), then report |
| A code from SMS/app | Assume account takeover; use the official recovery page |
| Installed a remote-access app | Disconnect from the internet, uninstall, call your bank |
| ID documents | Report identity theft |
| Intimate images | You're not to blame; don't pay; Take It Down (under 18) / StopNCII (adults) |
| An appointment / signature for a broker | You can cancel; don't cancel existing supplementary insurance; compare on priminfo.admin.ch |
| Just clicked, nothing entered | Usually fine; close it, enter nothing |
| Pasted someone else's sensitive data into an AI tool | Delete the chat and any shared link; check the training setting; tell your supervisor/data protection contact (the organisation may need to report it) |

Every card says **"this happens to careful people too."** Shame is a main reason seniors don't
report or call their bank in time.

Rules for resources:
- Every link must be **opened and checked by a person** before shipping (`verified` field in the JSON).
- Avoid "free unsubscribe" services that read your inbox; some have sold users' email data.
