# Scam scenario engine

> **Satellite file.** What to build for MVP 0 is defined only in [docs/mvp0-plan.md](mvp0-plan.md). This file holds the slot vocabulary, variation operators and seed ideas for later content packs. If this file disagrees with the plan, **the plan wins**.

> Status: **proposal** (brainstorm 2026-11-21). Goal: produce many varied scenarios from a small
> set of human-written ones, in a way that builds transfer, not just more of the same.

## Core idea: separate the hidden structure from what the message looks like

Every scam has a **hidden structure** (what makes it a scam) and a **surface** (how it looks).
Learners transfer when the surface changes and the structure stays the same. They learn something
new when one part of the structure changes, or when a clue they were relying on is removed.

A scenario is therefore a set of **slots**, not a block of text:

| Slot | Example values |
|---|---|
| `identity` (who they claim to be) | bank, parcel service, relative, recruiter, police/tax office, tech support, group admin, donor, NGO director, partner organisation, "neutral comparison service", insurance broker, pension advisor, ai_assistant (public chatbot), ai_app (unknown "AI helper" app, bot or extension) |
| `channel` | sms, whatsapp, email, phone_call, voicemail, telegram_group, marketplace, qr_code, letter, home_visit |
| `emotion` | fear, urgency, greed, love, authority, shame, helpfulness, curiosity, social_proof |
| `ask` | click_login, give_code, pay_fee, install_app, send_documents, move_money, forward_message, share_data, switch_channel, give_appointment, sign_contract, paste_data (into an AI tool), share_chat_link |
| `isolation` (optional) | "don't tell anyone", "stay on the line", "your bank advisor is involved" |
| `surface_clues` | typos, odd_sender, generic_greeting, or none (perfect language, real name, known contact) |
| `length` | single message, short exchange, slow build over days |
| `verify` (correct check) | number on card, relative's old number, official app opened yourself, colleague, in person, neutral official source |

The **four-step answer key comes from the slots, not from AI**. "Who" comes from identity and
channel, "emotion" from emotion, "ask" from ask, and "verify" from verify. Grading stays correct
however much text is generated.

### Scenario categories

- `scam`: fraud.
- `legit`: real message (legitimate twin), so learners don't conclude "everything is a scam".
- `misaligned_incentive`: legal or grey area, where the caller is paid by a commission that isn't
  in your interest (Swiss health insurance brokers, pension "optimisation"). Same four steps; the
  check is a **neutral official source**.
- `data_exposure`: **nobody is attacking**; the learner is about to leak sensitive data themselves,
  e.g. pasting patient or beneficiary data into an AI chatbot (idea from Ferran). The four steps
  still apply, which makes this the **furthest transfer test**: "Who receives this? What pressure am
  I under? What exactly am I giving? Is there a safer, approved way?"

## The concept that should "click"

> **A scam asks for something valuable and only gives you its own channel to check whether it's real.**
> A legitimate message either asks for nothing sensitive, or sends you to a channel you already control.

For misaligned incentives: **"Who pays this person, and for what?"** If the answer is "a bonus when
I sign", their advice isn't neutral; check a neutral source.

The "compare two cases" exercise aims to get learners to state this rule in their own words.
Typo-spotting doesn't transfer; this rule does.

## Variation operators

| # | Operator | What changes | What it teaches |
|---|---|---|---|
| 1 | Surface swap | identity, channel, wording; structure kept | Transfer: same trick, different costume |
| 2 | Emotion swap | fear becomes greed or love | Step 2 isn't about one emotion |
| 3 | Ask swap | click becomes "read me the code" or "install this" | Step 3: many kinds of ask |
| 4 | **Clue removal** | remove typos; add real name, perfect language, spoofed sender in the real thread | **Surface clues fail; only step 4 saves you.** Main difficulty ramp |
| 5 | **Legitimate twin** | same surface, but real (e.g. bank alert: "call the number on your card") | Prevents "everything is a scam"; forces use of the rule |
| 6 | Hijacked trusted sender | message comes from a real friend's or admin's account | Step 1 harder: the account is real, the person isn't |
| 7 | Channel switch | "continue on WhatsApp", SMS then a call | Scams move you to channels they control |
| 8 | Slow build-up | several turns over days, harmless start | The ask comes late |
| 9 | **New combination** | slot combinations never seen in training | **Transfer test** |
| 10 | Localise | same structure, scam variant common in that country and language | Cultural transfer (see [localization.md](localization.md)) |

## Pedagogical selection (what to show next)

### A. Grid of combinations, used to test transfer
Rows = `identity`, columns = `ask`. Train on some cells and hold others out for the test.
The Telegram "group moved" worm = hijacked trusted sender × click_login. If the learner trained
on "hijacked friend asks for a code" and "fake bank asks for a login" but never that combination,
catching it shows they combined two skills on their own. Show this grid on stage.

### B. The engine works against the learner's shortcuts
For each learner, track which surface features predict their "scam" answers (simple counts per
feature). If they flag everything with typos, the next scenario is a **legitimate message with
typos** (grandma's text) and a **perfectly written scam**. Adapting means finding and breaking the
shortcut the learner relies on, not just raising difficulty.

### C. Fading support per step
Per-step mastery decides which step prompts are still shown (see [idea.md](idea.md)).

## Seed scenarios (human-written; AI writes the variations)

### General
| # | Seed | Identity / channel | Emotion | Ask | Correct check |
|---|---|---|---|---|---|
| 1 | Fake bank SMS ([example](../content/scenarios/_example.json)) | bank / sms | fear, urgency | click_login | official app or card number |
| 2 | "Hi Mum, new number" | relative / whatsapp | love, urgency | pay_fee (urgent bill) | call the old number |
| 3 | Hijacked friend: "I sent you a code by mistake, forward it?" | known contact / whatsapp | helpfulness | give_code (account takeover) | call the friend |
| 4 | "Like videos for CHF 50/day" job | recruiter / whatsapp | greed | small paid tasks, then a deposit to "unlock" earnings | real employers never ask you to pay |
| 5 | Fake job asks for ID and bank details "for payroll" | employer / email | hope, authority | send_documents (identity theft) | official company site, real interview first |
| 6 | Fake police: "move your money to a safe account" | authority / phone_call | fear, isolation | move_money | hang up, call the official number yourself |
| 7 | Fake news: "forward before it's deleted" | unknown / group chat | outrage, fear | forward_message | check the source |
| 8 | **Held-out test:** "This group has moved, join here" | hijacked admin / telegram_group | mild urgency, social proof | click_login (spreads itself) | ask the admin through another channel |

### NGO pack
| # | Seed | Identity / channel | Emotion | Ask | Correct check |
|---|---|---|---|---|---|
| N1 | Fake director asks for gift cards or a new supplier IBAN | director (look-alike address) / email | authority, urgency | pay | call the director on a known number |
| N2 | Donor "overpays" 5,000 instead of 500, asks for the difference to another account | donor / email | helpfulness, guilt | move_money | wait for the payment to fully clear; refund only to the original account |
| N3 | "Partner organisation" asks for the beneficiary list | partner / whatsapp | helpfulness, authority | share_data | check with the coordinator; data-sharing rules |

### Switzerland pack (category mostly `misaligned_incentive`; some `scam`)
Facts marked ⚠ must be checked against official sources before shipping.

| # | Seed | Identity / channel | Emotion | Ask | Correct check |
|---|---|---|---|---|---|
| CH1 | **Health insurance cold call:** "Premiums are rising, a free comparison can save you CHF 1,200. When can an advisor visit?" Often a call centre (sometimes partly automated) that **collects appointments and sells them as leads** to brokers | "neutral comparison service" / phone_call | fear (premium rises), greed | give_appointment + personal data (age, insurer, family) | official premium comparator **priminfo.admin.ch**; you can switch basic insurance yourself by letter |
| CH2 | **Broker home visit:** "Just sign here to request an offer". The form actually also **cancels your supplementary insurance** | broker / home_visit | trust, urgency ("deadline is soon") | sign_contract | **Never cancel supplementary insurance before the new insurer accepts you in writing** (it can refuse you after a health questionnaire). Ask: "How are you paid?" |
| CH3 | **"Forgotten pension money":** a company offers to find your forgotten 2nd-pillar (LPP/BVG) money for a percentage | "pension recovery service" / letter or email | greed, curiosity | pay_fee (percentage) / sign power of attorney | **free** search at the Central Office for the 2nd pillar ⚠ |
| CH4 | **Leaving Switzerland:** an agency offers to withdraw your LPP for a percentage fee | agency / email, social ads | urgency, confusion | sign power of attorney + pay_fee | ask your pension fund / vested-benefits institution directly; it's free ⚠ |
| CH5 | **"Free pension optimisation":** leads to a 3a **life insurance policy linked to investments**, with long commitment, high upfront commissions and low value if cancelled early | advisor / phone_call, social ads | fear (retirement gap), greed (tax savings) | sign_contract | compare with a bank 3a account/fund; ask in writing for total costs and early-cancellation value ⚠ |
| CH6 | **Pension capital investment fraud:** "withdraw your pension for this real-estate/crypto project" | investment advisor / phone_call | greed, authority | move_money | **FINMA warning list** of unauthorised providers; your pension fund |

Swiss context to verify and cite ⚠:
- Since **1 September 2024**, the insurance industry agreement became binding for all insurers and
  their brokers. It **bans cold calls** for health insurance to people who aren't current or recent
  clients, and **caps broker commissions** (basic insurance: about CHF 70 per contract;
  supplementary: about one annual premium). This is the "real incentive" lesson: the broker's
  bonus is the reason for the call, not your savings.
- Calls that ignore the **asterisk (*) in the phone directory** count as unfair competition and can
  be reported (SECO).
- **Timing:** basic insurance can be switched for 1 January if the cancellation letter **arrives by
  30 November**. Cold-calling peaks in autumn, so that's when the seniors pack is most relevant
  (good hook for the pitch: the hackathon is in that window).

### AI and sensitive data pack (idea from Ferran; category mostly `data_exposure`)

Here the "message" is the learner's own action: the simulated phone or laptop shows an AI chatbot
with text pasted in and the cursor on "Send". The learner decides: send, edit first, or use another way.
(This is a scenario *object* the learner judges, not a chat with a tutor, so it respects the Scholé rule.)

Key questions to teach: **"Whose data is this?"** (yours: your informed choice; someone else's: not
yours to share) and **"Would I write this on a postcard?"**

| # | Seed | Who receives it / where | Pressure | What is given away | Safer way |
|---|---|---|---|---|---|
| AI1 | NGO volunteer pastes case notes (names, social-security/AVS number, residence status, health issues) into a free chatbot to write a support letter | public AI service, servers possibly abroad | time pressure, wanting to help | sensitive data **of beneficiaries** | remove identifying details first ("Client A", age instead of birth date, no AVS number); use the organisation's approved tool; follow the NGO's data policy |
| AI2 | Home-care worker or caregiver pastes a patient's name, diagnosis and medication list to build a schedule | public AI service | workload, convenience | health data of a patient (professional secrecy) | anonymise; use an approved tool; ask the supervisor |
| AI3 | Senior pastes **their own** lab results to understand them | public AI service | worry, curiosity | own health data | their choice, if informed: remove name and ID numbers, check the privacy settings (training opt-out, temporary chat), still confirm with the doctor. Teaches nuance, not fear |
| AI4 | **Scam crossover:** "free AI tax helper" website or Telegram bot asks you to upload your ID, payslip and tax return | unknown ai_app | greed (save time and money), authority | send_documents (identity theft) | official tax-office tools; check who runs the app. Same rule as the scams: valuable ask, no channel you control |
| AI5 | Volunteer shares a "link to the chat" in the team group so others can reuse the letter | anyone who gets the link | helpfulness | share_chat_link (the chat contains beneficiary data) | copy the anonymised final text only; shared links may be visible to anyone who has them ⚠ |
| AI6 | AI note-taker or transcription app joins a counselling call with a beneficiary | ai_app / meeting tool | convenience | recording of a confidential conversation | ask consent; use only approved tools; don't record sensitive sessions |

Legitimate twins: same task with anonymised text in an approved tool, or the senior removing their
name before asking about their own results.

Risks to explain (check each ⚠ before shipping):
- Depending on the service and settings, pasted text **may be stored, reviewed by people, or used to
  train models**. Consumer and free plans differ from organisation plans with a data-processing agreement.
- Anyone who takes over the account (phishing, see the other packs) **can read the full chat history**.
- Shared chat links have ended up **indexed by search engines** in past incidents ⚠.
- Employees have leaked confidential material by pasting it into chatbots (widely reported case:
  engineers pasting source code, 2023) ⚠.
- **Legal (Switzerland):** under the revised data protection law (since 1 September 2023), health
  data counts as **sensitive personal data**; sending it to a provider abroad has extra rules;
  professional secrecy applies to health professionals; use of the AVS number is restricted.
  Organisations may have to report serious data breaches to the FDPIC. EU: GDPR ⚠.
- Fake "AI" apps, bots and browser extensions are a growing way to collect data (links AI4 to the scam packs).

### Legitimate twins (write one for each main seed)
- Real bank fraud alert: no link; "call the number on the back of your card".
- Real relative with a new phone who's happy to be called back on the old number.
- Real supplier changing banks who expects a call-back on a known number.
- Real health insurer letter announcing the new premium, pointing to priminfo.admin.ch.

## Generation pipeline that uses AI sparingly (AXA)

1. **People write the seeds**, each tagged with its slot values.
2. **Code, not an AI, picks the variation:** the learning sequence chooses the operator and slot changes.
3. **A small AI model only writes the text**, from the seed, target slots, language/country and
   style rules (fictional brands only, `.example` links, length).
4. **Rule-based checks** confirm the text matches the spec (link present if `click_login`, no real
   brand names, urgency words present), then a person spot-checks.
5. **Generate in advance and cache.** The demo makes no live AI calls, so it's cheap and nothing breaks on stage.

## Open questions
- Phone calls as audio (text-to-speech)? Covers "AI voice clone of a grandchild" and adds a second
  input type for AXA.
- Demo scope: 3–4 seeds fully worked, about 20 cached variants, legitimate twins and the held-out test.
