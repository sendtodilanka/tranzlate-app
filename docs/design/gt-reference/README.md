# GT 2026 gold-reference captures (owner's recording + screenshots, 2026-07-22)

Evolved Google Translate (iOS) UI — the D-0 north-star's CURRENT form (BUILD_ROADMAP "GT gold-reference capture" item).

## Home hub (owner SS, 8:17 frame)
- Top bar: bookmark/Saved (left) · centered "Google Translate" wordmark · profile avatar (right)
- Huge "Enter text" placeholder + handwriting icon; big quiet canvas (no headline, no cards)
- Bottom: language chips [English ⇄ Sinhala] → action row: "Live translate" · **center primary MIC (large blue circle)** · "Camera" (labeled buttons)
- **NO bottom navigation bar on phone** — GT uses a hub model: everything 1 tap from Home (Saved icon, avatar→account/settings, mic/camera/live). **We match this again as of 2026-07-26 (D-5 rev.3, issue #42):** Tranzlate's phone build has no bottom bar and no drawer either — Home's card stack is the hub. ~~**Tranzlate deliberately deviates here** (owner, D-5 rev.2 / issue #26): our phone build adds a persistent bottom nav (Home/Chat/Camera) — an intentional departure from GT.~~ **That deviation is withdrawn.** The rest of GT's hub (quiet canvas, top-bar Saved/avatar) still informs our design — though our hub is a *card stack*, not GT's single-placeholder canvas, and that composition difference is intentional (the design brief's uniqueness mandate).

## Translate session screen
- "< Home" pill (left) · History ⏱ + bookmark + ⋯ (right); while typing: edit/handwriting + ✕ + ⋯
- SOURCE side is rich too: "English" label → large input text → phonetic ("həˈlō") → speaker + copy
- Divider → "Sinhala" label → blue result ("ආයුබෝවන්") → transliteration ("āyubōvan") → speaker · copy · 👍👎 feedback · ⋯
- **"Examples of <word>"** enrichment card below the result (dictionary-style; quote rows)
- **"+ New translation"** pill bottom-right = next entry (session/turn model)
- Language chips at BOTTOM above keyboard; **history quick-recall row** ("🕘 Hello, man. / හෙලෝ මචන්." + expand ↖) sits above the chips while typing
- LIVE translate-as-you-type (no send button on the free path)

## Visual
Near-white/gray-blue surfaces, white pill controls, Google Blue accents (= our P1 palette, docs/design/PALETTES.md).

## Tranzlate deltas to layer on (do not break the above)
Mode chip (Automatic ▾) + metered "15/20 today" counter + explicit send for Advanced AI (C-2) · Settings/Paywall entries · white-label branding · **a card-stack hub** (tool grid + list rows) where GT has a single quiet canvas — the composition is our identity, not a GT pattern.
**Spec reconciliation (updated 2026-07-26, issue #42 / PR #43):** DECISIONS **D-5 rev.3** removes the bottom nav *and* the drawer; Home's card stack is the hub. On the bottom-bar question we are **back in line with GT** — the rev.2 deviation is withdrawn, and the one remaining recorded deviation is the explicit-Translate button (D-0 / C-2). CLEAN_ROOM_UX §1/§2/§3, spec-01 §2/§9, UI_SPEC §2.1, DESIGN_SYSTEM §9 and DESIGN_TOKENS §7 are aligned to rev.3. ⚠ Rail/permanent drawer for Medium/Expanded is **no longer specified** — wide-window IA needs its own design round.
~~**(2026-07-24, issue #26):** D-5 rev.2 records a persistent bottom nav on Compact (Home/Chat/Camera) — a deliberate deviation from GT's no-phone-bottom-bar hub. Rail/drawer stays for Medium/Expanded (C-13).~~

Frames: gt2026-live-typing.jpg · gt2026-result-session.jpg · gt2026-definitions.jpg · gt2026-long-text.jpg (from 61.6s video). Internal design reference only — not shipped assets.

> Source screen-recording videos are kept OUT of git (size) — owner retains originals; extracted frames + screenshots here are the reference.
