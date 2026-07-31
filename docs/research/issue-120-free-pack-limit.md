# Research — issue #120: should the free tier be limited to N offline packs?

status: method agreed, evidence in progress
read-only record (Rule 4) — no code changes are made from this file

## 0. Why this document exists

The owner was asked to rule on a paywall the design guideline draws ("Free keeps
2 packs. Pro downloads all 65"). Their answer was to ask for a *process* rather
than an opinion:

> "I am not a business strategist, technical expert, or any other professional,
> I require your assistance in making a decision. You should design a method,
> strategy, or system that determines what data is required, collects that data,
> analyzes it, and ultimately reaches a final decision."

So the first deliverable is the method, and the method is written **before** the
evidence is read. That ordering is the whole point: a decision rule invented
after the numbers arrive is not a rule, it is a rationalisation.

## 1. The decision, stated precisely

**Should the free tier of Offline Translator be limited to a maximum number of
simultaneously-downloaded offline language packs, with the limit removed by the
Pro subscription?**

Options on the table:

| | Option | What it means |
|---|---|---|
| **A** | No pack limit | Status quo. Free users download as many packs as they want. Pro keeps gating what it gates today. |
| **B** | Hard count limit (the guideline's proposal) | Free tier holds at most N packs (N=2 drawn in one frame, N=3 in another). Exceeding it opens the replace-or-upgrade sheet. |
| **C** | Limit only for NEW installs, existing users grandfathered | Same as B, but nobody loses anything they already have. |
| **D** | A different offline-flavoured paid proposition | Keep packs unlimited; sell something else that fits "offline" (see §5, Q4). |

Note that B and C are materially different products, and conflating them is the
most likely way to get this wrong.

## 2. What makes this decision expensive to reverse

Recorded up front, because these constrain the rule in §4.

1. **It is a REMOVAL, not an addition.** Verified in the live source: there is
   no `packLimit` / `maxPacks` / `downloadLimit` anywhere in v1.0.4, and the
   download screen's only entitlement call refreshes state without gating
   anything (`DownloadViewModel` `init` → `checkEntitlements()`). Live users can
   download unlimited packs **today**. Option B takes that away in an update.
2. **The app's NAME is the feature being gated.** The Play listing is "Offline
   Translator".
3. **The paid tier already gates a different axis** — translation volume on the
   paid cloud engine; the free engines never charge quota. B adds a second,
   unrelated restriction, which makes the value proposition harder to state, not
   easier.
4. **We cannot measure the behaviour we would be pricing.** `firebase-analytics`
   is deliberately absent from the build, so "how many packs does a typical user
   download?" has no answer from our own telemetry. Any rule that depends on
   that number must say how it will be obtained, or must not depend on it.

## 3. The evidence required, and how it gets collected

Split by who can actually obtain it — the honest part of the method.

### 3a. Obtainable by me, now

| # | Question | Method | Status |
|---|---|---|---|
| E1 | Does the live app limit packs? | Read v1.0.4 source | **Done — no limit** |
| E2 | What does our Pro tier gate today? | Read `UsagePolicy` / `TranslateTextUseCase` | **Done — translation volume on the paid engine only** |
| E3 | What do comparable translator apps do about offline packs — free, paid, or count-limited? | Competitive sweep with sources; Google Translate is the benchmark | In progress |
| E4 | Is a count-limit on previously-unlimited content a known pattern or anti-pattern? Evidence on retention and review impact when an app introduces one in an update | Literature and case-study sweep | In progress |
| E5 | Play policy exposure: gating a capability the app is named for; removing functionality from existing users | Play Subscriptions + Deceptive Behavior + store-listing policies | In progress |
| E6 | What DO successful translator apps sell instead? | Competitive sweep | In progress |
| E7 | How many packs can a device realistically hold? | Measured pack size range (20–45 MB observed; one measured pair = 45.7 MB) vs typical free storage | Partly known |

### 3b. Obtainable only by the owner (Play Console)

These need the owner to read numbers I have no access to. **None of them is
needed for the §4 rule to fire — they refine the answer, they do not gate it.**

| # | Question | Where |
|---|---|---|
| O1 | Current Pro conversion rate and monthly revenue | Play Console → Financial reports |
| O2 | Install base size, and retention/uninstall trend | Play Console → Statistics |
| O3 | Current rating, and whether reviews mention offline or paywall | Play Console → Ratings & reviews |
| O4 | Whether any past update caused a rating drop | Play Console → Ratings over time |

### 3c. Obtainable by nobody today

| # | Question | Why not | Consequence |
|---|---|---|---|
| N1 | How many packs does a typical user download? | No analytics in the build | **The rule in §4 must not depend on this.** If it turns out to be decisive, the honest answer is "instrument first, decide later" — which is itself a valid outcome. |
| N2 | Would a pack-limited free tier convert better than today's? | Requires an A/B test we cannot run pre-launch | Same. |

## 4. The decision rule — written before the evidence

Agreed in advance. Each clause is a filter; the first one that fires decides.

**R1 — Competitive floor.** If the benchmark free competitor (Google Translate)
offers unlimited free offline packs, then Option B prices *below zero*: we would
charge for something a better-resourced free app gives away, in an app whose
whole positioning is offline. **B is rejected outright.** No revenue projection
overrides this, because the user's alternative is one tap away in the same store.

**R2 — Removal test.** If the live app already gives users unlimited packs, then
B is a removal from existing users. Removals require a positive case, not a
neutral one: B may only proceed if the evidence shows the limit *increases*
retention or revenue, and the burden of proof is on B. Absent that evidence, B
falls back to **C at best**, never B.

**R3 — Policy gate.** If gating a capability named in the store listing creates
Play policy exposure, B and C are both rejected regardless of any revenue case.
A store takedown costs more than any subscription upside.

**R4 — Measurement honesty.** If the decision turns on a number we cannot
measure (N1), we do not guess it. We either choose the option that does not
depend on it, or we instrument first and revisit. "We think most users only
download two" is not evidence.

**R5 — Alternative test.** If a different paid proposition (Option D) is
available that does *not* fight the app's positioning and is already supported by
our architecture, it is preferred to any option that does.

**Pre-registered consequence:** if R1 fires, the answer is A or D, and the design
guideline's sheet 19e plus every "Free keeps N packs" line comes out of the spec.

## 5. Findings

*(Filled as evidence lands. E1, E2 and E7 are complete; E3–E6 are with the
competitive sweep.)*

### E1 — the live app has no pack limit ✅

Verified by source read of `~/StudioProjects/Tranzlate` (read-only reference,
Rule 2). No `packLimit`, `maxPacks`, `downloadLimit`, `freeLimit` or equivalent
exists. The download screen's sole entitlement interaction is a state refresh in
`DownloadViewModel.init`. **Unlimited free packs is the shipped behaviour.**

### E2 — our Pro tier gates translation volume, not packs ✅

`UsagePolicy` / `RealUsagePolicy` charge quota only against the paid cloud
engine; the offline and free-online engines never charge it
(`TranslateTextUseCase`). Offline capability is, by construction, outside the
paywall today.

### E7 — how many packs fit? (partial)

Observed pack sizes are roughly 20–45 MB, with one directly measured pair
(`de_en`) at 45.7 MB covering two languages
(`docs/research/issue-90-offline-download-lifecycle.md` §E3). A limit of 2 saves
on the order of 60–90 MB against a user who would otherwise hold 4. On a modern
device that is not a meaningful storage argument, so **storage pressure cannot be
the justification for a limit** — the justification would have to be purely
commercial.

### E3 — nobody in this category limits offline packs by COUNT ✅

Eleven apps checked with sources. The market has exactly two models, and the
proposal belongs to neither:

| Model | Apps |
|---|---|
| Offline **free and unmetered** | Google Translate, Microsoft Translator, Naver Papago, Yandex, Offline Translator: Transee |
| Offline **entirely behind the subscription** (a boolean, not a count) | iTranslate, Reverso, PROMT.One, Translator Offline (iOS) |

The benchmark is unambiguous. Google's own help page: *"You can download
languages onto your device. This lets you translate them without an internet
connection."* — no cost, no subscription, no quantity cap, and even the
higher-quality pack upgrade is free
([support.google.com/translate/answer/6142473](https://support.google.com/translate/answer/6142473?hl=en&co=GENIE.Platform%3DAndroid)).

**Honest weakening, recorded:** Google and Microsoft never write the word
"unlimited". "No count limit" here is a *documented absence*, not a positive
claim. It does not change the user's position — a free user who hits our third
pack can install Google Translate at no cost — but the distinction is real and
is not hidden.

### E4 — the pattern exists, but the analogue that works is not this one ✅

**The strongest evidence FOR a limit**, surfaced deliberately rather than buried:
**OsmAnd** has run exactly this model for over a decade — *"7 map downloads"*
free, *"Unlimited map downloads"* paid
([osmand.net/docs/user/purchases/android](https://osmand.net/docs/user/purchases/android)).
Offline-first, download-pack, count-limited, commercially viable. Two caveats
that matter: the limit is **7, not 2**, and OsmAnd is a prosumer tool for
hikers and field workers, not a mass-market travel translator.

**The evidence AGAINST introducing one into a previously-unlimited app is
consistent and severe** — and there is a natural experiment in it:

| App | Change | Grandfathered? | Outcome |
|---|---|---|---|
| Dropbox (2019) | unlimited devices → 3 | **Yes** — existing links kept | Criticism, no collapse |
| Evernote (2023) | 100,000 notes → 50 | **No** | Mass migration; the company itself conceded users may "reconsider your relationship with Evernote" |
| Pushbullet (2015) | free features → Pro | No | *"never really recovered from the massive drop in popularity"* |

Same structural change, opposite outcomes, and the variable is grandfathering.

On gate *shape*: a 2-pack cap is neither a feature gate nor a usage limit — it
is a **one-time setup wall, hit on day one before any habit forms, that never
grows with the value the user is getting.** Usage limits convert because
pressure rises with value received; a day-one wall just reads as a locked door.

### E5 — Play policy: no per-se violation, but two real exposures ✅

- **Name vs paywall** — not a violation on its own. An app called "Offline
  Translator" that translates offline is accurate even with a cap. The
  Deceptive Behavior policy bites on *misrepresentation*, and there is none here.
- **Disclosure is mandatory, not optional.** The Subscriptions policy requires
  "clearly and explicitly disclosing… whether a subscription is required to use
  the app", and the Payments policy requires the listing to "clearly notify
  users that payment is required to access those features". A limit means the
  Play listing must say so, up front.
- **⚠️ The one that actually threatens the design:** *"Subscriptions must
  provide sustained or recurring value… and may not be used to offer what are
  effectively one-time benefits to users."* "Unlock all packs" is **inherently a
  one-time benefit** — the user downloads once and is done. Binding it to a
  recurring SKU is a coherence problem the current cloud-volume gate does not
  have, because volume genuinely recurs.
- **No Play rule was found requiring grandfathering** or forbidding the removal
  of free functionality. So removal is a product and reputation risk, not a
  policy one.

### E6 — what the category actually sells, and what fits "offline" ✅

Volume/character quota (DeepL, Reverso) · higher-quality engine · modality
bundles (camera, voice, conversation) · document translation · history and
export · **privacy guarantees**. The first two are what our Pro already gates.

The privacy angle is the underused one and it is the *opposite* of a gate:
"offline means your words never leave the phone" is something to sell the
product on, not something to charge for.

## 7. Outcome — decided 2026-07-31

**Option A: no pack limit. The paid tier stays what it is (Option D), which is
what we already built.**

### Which rule fired

**R1 (competitive floor) fires, and it decides.** The benchmark free competitor
gives away, at no cost and with no documented cap, precisely what Option B
proposes to charge for — in an app whose entire positioning is offline. A free
user who hits the third-pack wall has two options: pay us, or install Google
Translate. The second is free, better resourced, and may already be on the
phone. Under the rule as written before the evidence, **B is rejected outright
and no revenue projection overrides it.**

Three further rules confirm rather than contradict:

- **R2** — the live app has no limit (E1), so B is a *removal*. The burden of
  proof was on B and the evidence runs the other way (Evernote, Pushbullet).
  B could at best fall back to C, and R1 has already excluded both.
- **R4** — the strongest overturn condition (">90% of active free users never
  download a third pack") depends on a number **we cannot measure**, because
  the build deliberately ships no analytics. The rule says: do not guess it.
  Evernote made exactly this argument — "the majority of our Free users fall
  below the threshold" — and it did not save them, partly because dormant
  accounts flattered the count.
- **R5** — a non-conflicting paid proposition exists and is already
  implemented: cloud volume, engine quality, modalities, privacy.

**R3 does not reject, but it adds a finding worth keeping even now:** the
"sustained or recurring value" rule makes a one-time pack unlock a poor fit for
a subscription SKU in general. That is an argument against ever reaching for
this lever, not just today.

### The part of the design guideline's instinct that WAS right

The spec's storage concern is real, and ML Kit's own documentation agrees:
*"Language models are around 30MB, so don't download them unnecessarily…
**Avoid keeping too many language models on the device at once.**"*
([developers.google.com/ml-kit/language/translation/android](https://developers.google.com/ml-kit/language/translation/android))

That is a mandate for **storage-hygiene UX** — an unused-pack cleanup prompt, a
library meter, a "you have not used this in 6 months" nudge — none of which
needs a paywall, and all of which the Manage packs page should carry. The
instinct was sound; the lever was wrong.

### What this closes

- Sheet **19e** (free-limit paywall) is cut from the spec.
- Every "Free keeps N packs" line is cut (and the guideline's own 2-vs-3
  contradiction becomes moot).
- The designer brief's open question 1 is answered.

### What would reopen it

Recorded so this is falsifiable, not final by fiat. Three of these four would
have to hold:

1. Measured evidence — from analytics we do not currently ship — that >90% of
   **active** free users never download a third pack.
2. Full grandfathering of existing users, Dropbox-style.
3. A limit in OsmAnd's range (7), not 2. Two packs is one language *pair*; that
   is a demo, not a free tier.
4. A repositioning of the product away from mass-market travellers toward the
   prosumer/offline-power-user niche where the count-limit precedent exists.

If someone wants to revisit this, that is the bar.

## 6. Disconfirmation — what would make the limit the RIGHT answer

Stated now so the conclusion can be attacked later:

- Google Translate turns out to *also* limit or charge for offline packs → R1
  does not fire, and B becomes arguable.
- Evidence that translator apps which introduced pack limits saw retention or
  revenue improve, with sources → R2's burden is met.
- Play policy turns out to be indifferent to name-vs-capability gating → R3 does
  not fire.
- The owner's Play Console data shows Pro conversion so low that the current
  proposition is demonstrably not working, AND no Option D exists → the risk
  calculus changes.

If none of these hold, the recommendation stands and this record says why.

## 7. Outcome

*(To be written once E3–E6 land. The outcome section must state which rule fired
and on what evidence — not a summary of preferences.)*
