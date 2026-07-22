# Tranzlate — Clean-Room UX Requirements (corrected)

> Derived from a **standards evaluation** of the live app's core features against Material 3 / Android / Google / WCAG (2026), grounded in captured screenshots + code.
> **Verdicts: KEEP 0 · FIX 39 · REDESIGN 19** across 58 patterns. **Zero patterns were correct as-is** → the existing UI is a *reference*, not a spec.
> Status: generated 2026-07-21 · uncommitted · feeds the clean-room build.

---

## 1. Clean-room UX principles (non-negotiable)

1. Value before ask: never gate first-run behind fake loading, a hard paywall, or a consent wall. Route onboarding straight into a working first translation; defer every monetization and consent prompt to the contextual moment it is actually needed, always dismissible.
2. Honest signalling only: no fabricated waits or misstated durations, no overclaimed capability counts (state true on-device ~50 offline languages separately from online counts), no stale hardcoded version strings, no fake conversational-AI framing on a deterministic translator.
3. Navigation reflects information architecture: every peer translation mode is one tap away and never hidden. **[Amended 2026-07-22, D-5]** On Compact this is delivered by the HUB model (composer = Text/Voice, canvas tiles = Camera/Conversation) rather than a bottom bar; the drawer holds secondary destinations only (Search, History, Saved, Offline languages, Settings) — the "drawer never hides peer tasks" intent is preserved.
4. Adaptive by default via window size classes (rememberWindowInfo), never hardcoded dp breakpoints: hub model on Compact (D-5), rail on Medium, permanent drawer on Expanded; constrain reading width and use two-pane (ListDetail input+result, paywall value+plans) on Medium/Expanded; support landscape, multi-window, and foldable postures.
5. Controls must look, feel, and measure like controls: real Material 3 components (Button/AssistChip/SegmentedButton/RadioButton) with visible affordance, ripple/state feedback, >=48dp touch targets, and correct role semantics. No noRippleClickable status-badge disguises for revenue-critical controls like the model switcher.
6. Accessibility is a gate, not a polish pass: every actionable icon carries a correct localized contentDescription; decorative art is null; selected/disabled/step states are exposed via semantics (Role.RadioButton, stateDescription, live regions); color is never the sole differentiator; contrast meets WCAG 4.5:1 (no alpha-only dimming below the floor).
7. Zero hardcoded user-facing strings: 100% of copy comes from strings.xml with fil and pt-rBR translations and format/plural args; dispatch logic (menu actions, mode selection) keys off stable enums/ids, never off localized label text.
8. Cost, entitlement, and limits are legible before commitment, never surprises: show live 'N/20 today' counters and lock/Premium chips on metered modes, default fresh free users to a graceful AUTO mode, and disclose price, billing period, trial, and renewal terms directly under any purchase CTA (Play policy).
9. No dead ends or dead paths: every state has a next action (skeleton while loading, inline error + Retry on failure, enabled CTA once purchasable); no permanently disabled terminal CTA, no silent truncation, no inert IME action, no empty onSubscribe lambda, no unreachable dialog left in the tree.
10. Respect user intent and system gestures: never hijack Back to inject an interstitial, never attach ads to utility navigation. Ads fire only on genuine content-completion (post-translation) with a frequency gap plus daily impression cap, and Firebase consent signals are derived strictly from the UMP outcome with a Settings withdrawal entry point.

---

## 2. Top redesigns (must NOT be copied — rebuild)

### ▸ Immediate hard paywall + fake 'Personalizing' gate as first-run entry
- **Why:** Two stacked blockers (fabricated delay, then unskippable paywall with cleared back stack) ask for money before any value is experienced — a roach-motel that breaks the value-before-ask principle and erodes first-run trust.
- **Corrected:** Delete the fake delay; onboarding flows directly into a working first translation. Monetization becomes a soft, always-dismissible paywall triggered contextually (premium-gated action or after first successful translation) with a visible Close / 'Maybe later'.

### ▸ Drawer-only primary navigation hiding four peer translation modes
- **Why:** Text/Camera/Dialog/History are equal top-level tasks but are scattered across content cards, toolbar buttons, and a modal drawer with no shared mental model; Camera/Dialog vanish once you leave Home, and large screens waste space.
- **Corrected [amended 2026-07-22, D-5]:** Compact = hub model — peer modes visible ON the hub (composer + quick-action tiles), drawer strictly secondary. Medium = rail, Expanded = permanent drawer (NavigationSuiteScaffold), with saved state; on Expanded, Text uses ListDetailPaneScaffold (input pane + result pane). The failure being corrected was peers HIDDEN drawer-only behind an empty canvas — the hub model removes the hiding, not the drawer.

### ▸ Paywall renders empty, CTA permanently disabled, terms undisclosed
- **Why:** The primary plan card shows no plan/price/trial because real offerings data is never routed to it, the only CTA is a dead disabled control with no retry, and price/renewal/trial are never shown — an unpurchasable dead end and a Play policy compliance failure.
- **Corrected:** Load offerings on screen entry with a skeleton state; render plan name + prettyPrice + period once resolved; enable the CTA only when a purchasable product exists; show a full-width Retry on load failure. Disclose exact price, billing period, trial, and 'then {price}/{period}, auto-renews, cancel anytime' directly under the CTA.

### ▸ Default free user placed on the metered NLP3.5 online mode
- **Why:** The default silently consumes a premium 20/day quota the user never chose, fails entirely offline, misrepresents the free entitlement, and surfaces an out-of-nowhere paywall on exhaustion — a monetization-integrity and trust defect.
- **Corrected:** Default a fresh free user to AUTO (Tranzlate Auto), which picks a free engine and degrades gracefully offline. NLP3.5 becomes explicit opt-in with a persistent lock/Premium chip and live 'N/20 today' counter shown before commitment. Rename modes around benefits (Automatic / Offline / Standard online / Advanced AI premium), not version strings.

### ▸ Interstitial ad on Back-press and on every utility-screen transition
- **Why:** Hijacking the system Back gesture breaks predictive-back, delays intended navigation, encourages accidental clicks, and risks AdMob policy strikes; treating language/settings navigation as an ad break is the annoying 'ad after every action' anti-pattern.
- **Corrected:** Never attach ads to Back or to utility navigation. Bind ad eligibility to a task-completion event (translation delivered) with the 15-minute gap plus a daily max-impression cap. Camera/Language/Settings navigation stays ad-free.

### ▸ Firebase ad/analytics consent force-enabled regardless of UMP choice
- **Why:** Setting Firebase ad-personalization consent to true even after the user taps 'Do not consent' directly contradicts the user's choice — a trust and legal (GDPR) breach.
- **Corrected:** Derive all Firebase consent signals (adStorage / adUserData / adPersonalization) from the UMP form outcome, persist them only in the form-completion callback, remove the unconditional =true, and add a Settings 'Privacy / Ad consent' entry calling showPrivacyOptionsForm when isPrivacyOptionsRequired is true.

### ▸ Home headline as chat-assistant framing with a hardcoded rainbow gradient
- **Why:** The decorative conversational-AI headline sets a false expectation for a deterministic translator and gives zero task orientation, while the non-token multi-stop gradient bypasses theming and risks failing text contrast on the light surface.
- **Corrected:** Task-first empty state from strings.xml (quiet 'Translate' heading + 'Type or paste text to translate' helper), or drop the headline and let a focused input lead. Any brand accent uses Phase 4 GradientColors tokens within on-surface contrast guidance.

---

## 3. Corrected per-feature requirements (the clean-room spec)

### onboarding
Skip fake setup; flow directly into an interactive first translation. Optional <=3 differentiated benefit screens (offline, camera, live conversation) with re-enabled swipe and a linear stepper exposing 'Step X of N' via semantics. All copy in strings.xml (en/fil/pt-rBR); true 'offline for 50+ languages' claim separated from online counts. Material 3 color roles with validated light/dark contrast and dynamic color. Adaptive: centered width-constrained column or two-pane art+text on Medium/Expanded, responsive imagery. Decorative art contentDescription=null; logo meaningfully labeled. No paywall in the onboarding path.

### home-text
Text is a top-level destination in NavigationSuiteScaffold (no ModalNavigationDrawer as sole nav); Expanded uses ListDetailPaneScaffold input+result. Task-first empty state from strings.xml, no chat framing, no rainbow gradient. Single/multi-line decision made deliberately; IME action wired to translate plus an always-visible labeled >=48dp Send/Translate button. Live character counter (e.g. 480/500) with inline limit messaging, never silent truncation; remove unreachable upsell dialog. Model switcher is a real AssistChip/SegmentedButton with ripple, accessible name ('Translation model: ...'), and >=48dp target, placed near the input. Correct localized contentDescriptions on all icons.

### mode-picker
Standard bottom sheet with DragHandle and a separate 'Switch model' header. Four modes named by benefit/attribute (Automatic / Offline on this device / Standard online / Advanced AI premium) with meaningful attribute chips. Fresh free users default to AUTO. Persistent trailing state on NLP3.5: lock/Premium chip for non-subscribers plus live 'N/20 today' counter, always visible (no alpha-only disabled dimming; use lock icon + inline reason at readable contrast). Rows wrapped in selectable(Role.RadioButton) in a selectableGroup with announced selected state. Quota-exceeded copy grammatical and specific ('used all 20 free NLP3.5 translations today ... resets at midnight'). All strings localized.

### paywall
Load offerings on entry with a shimmer/skeleton; render plan name + prettyPrice + period on Success; enabled CTA only when purchasable; dedicated error state with full-width Retry on failure. Two selectable plan cards each showing own price + key benefits, Plus badged 'Recommended' and pre-selected. Disclose price, billing period, trial, and 'then {price}/{period}, auto-renews, cancel anytime' under the CTA (Play policy). Restore and Details as TextButtons >=48dp; footer links correctly mapped (Terms->termsCondition, Privacy->privacyPolicy) with Link role and a test. Benefit icons contentDescription=null. Content max-width ~480dp centered on Compact; real two-pane (value | plans+CTA) on Medium/Expanded; fixed spacing not weight spacers. All strings in strings.xml with format args.

### dialog
Instructive empty state naming the action and per-side languages, with the two mics as the obvious primary affordance and a live region announcing the first message. Each mic gets a distinct localized description built from the language name ('Speak English'/'Speak Spanish'). Menu actions dispatched by stable enum/id, never localized label — fixing dead Edit/Delete in non-English locales. Language selectors use FilledTonalButton/outlined-pill tokens (not 0.03 alpha). Per-bubble semantics state language/side; side differentiation not color-only. Model selector is an interactive AssistChip/top-bar action with real press states. Usage-limit copy localized and grammatical with a wired Upgrade CTA (no empty lambda), shown as dialog/inline banner. Conversation constrained to readable max width and centered on Medium/Expanded.

### ads-consent-placement
No ad on Back-press or utility-screen navigation; ad eligibility bound to translation-completion events only, gated by a 15-minute gap and a daily max-impression cap. Firebase consent (adStorage/adUserData/adPersonalization) derived strictly from the UMP form outcome and persisted only in the completion callback — remove the unconditional =true. Settings exposes a 'Privacy / Ad consent' item calling showPrivacyOptionsForm when isPrivacyOptionsRequired. Consent gate presented at the moment ads are actually requested (or consolidated into one guided sequence), not stacked as a third first-run full-screen blocker.

### navigation-ia
Persistent NavigationSuiteScaffold with the real top-level destinations (Text, Camera, Dialog, History) driven by rememberWindowInfo: bottom bar (Compact), rail (Medium), permanent drawer (Expanded) — no hardcoded dp breakpoints, no drawer-only primary nav. Drawer holds destinations/secondary items only, not browsable recent translations. All top-level switches route through one helper applying launchSingleTop + popUpTo(start){saveState=true} + restoreState=true; only detail screens (Result, Language picker) are real pushes. Model selection is an explicit labeled chip/app-bar action with contentDescription and >=48dp target. Drawer footer renders 'Tranzlate v${BuildConfig.VERSION_NAME}'.

---

## 4. All REDESIGN patterns (evidence)

| Severity | Feature | Pattern | Corrected |
|----------|---------|---------|-----------|
| blocker | onboarding | Fake 'Personalizing Your Experience' loading gate | Delete the fake delay entirely. If genuine setup exists, show a determinate indicator only while actually work |
| blocker | onboarding | Immediate hard paywall as first interactive screen (value-be | Send onboarding directly into a working first translation. Defer monetization to a soft, always-dismissible pa |
| blocker | home-text | Top-level peer destinations surfaced as body content cards,  | Promote Text, Camera, Dialog (plus History) to a single NavigationSuiteScaffold as top-level items; drop the s |
| major | home-text | ModalNavigationDrawer as sole primary navigation on all wind | Replace the ModalNavigationDrawer with NavigationSuiteScaffold; on expanded widths consider ListDetailPaneScaf |
| major | home-text | Chat-assistant framing on a deterministic translator | Use a task-first empty state sourced from strings.xml, e.g. a quiet heading like 'Translate' with helper text  |
| blocker | mode-picker | Default selected mode for a fresh free user | Default a fresh free user to AUTO (Tranzlate Auto) — the smart mode that picks a free engine and degrades grac |
| major | mode-picker | Mode naming (product/engineering jargon) | Rename around benefit + attributes: e.g. 'Automatic', 'Offline (on this device)', 'Standard (online)', 'Advanc |
| blocker | paywall | Paid-plan card renders empty (no plan / price / trial) | Route the real collected `response` into PlanSelector/PaidPlan; add an explicit Idle/Progressing branch that s |
| blocker | paywall | Disabled primary CTA as a dead-end (no recovery) | Trigger offerings load on screen entry; show the CTA in a loading state while fetching, enabled once a purchas |
| blocker | paywall | Price, billing period, trial & renewal terms not disclosed | Once offerings resolve, display exact price, billing period, trial duration and the explicit 'then {price}/{pe |
| major | paywall | Segmented toggle used for plan comparison with weak hierarch | Use two selectable plan cards; each card carries its own price + key benefits; badge the Plus card 'Recommende |
| major | paywall | Non-adaptive layout + dead large-screen branch | Constrain content to a max width (~480dp) centered on Compact; on Medium/Expanded use a real two-pane layout ( |
| minor | paywall | Load-failure retry buried inside a tiny toggle segment | Replace the whole plan/CTA region with a dedicated error state (message + full-width Retry that calls fetchSub |
| blocker | ads-consent-placement | Interstitial ad fires on Back-press / screen exit | Back button එකට ad එකක් attach කරන්නම එපා. Ad show කරන්නේ ඇත්ත content-completion transition එකකදී විතරයි (උදා |
| major | ads-consent-placement | Interstitial attached to every utility-screen transition | Ad eligibility එක task-completion event එකකට bind කරන්න (translation එකක් සම්පූර්ණ වීම), screen navigation එකක |
| blocker | ads-consent-placement | Firebase ad/analytics consent force-enabled independent of U | UMP form outcome එකෙන් Firebase consent signals derive කරන්න (adStorage/adUserData/adPersonalization = user ch |
| blocker | navigation-ia | Drawer-only primary navigation (no navigation bar / rail) | Adopt a persistent bottom NavigationBar (via NavigationSuiteScaffold) exposing the 3-5 real top-level destinat |
| major | navigation-ia | Primary features exposed as in-content cards & toolbar butto | Model Text / Voice / Camera / Dialog as sibling top-level destinations in the NavigationSuiteScaffold so any m |
| major | navigation-ia | No adaptive navigation — window size classes absent (violate | Wrap the primary destinations in NavigationSuiteScaffold driven by rememberWindowInfo: bottom bar (Compact), n |

> Full FIX-level findings (39) live in the workflow journal; folded into §3 corrected specs.