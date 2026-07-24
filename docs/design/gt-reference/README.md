# GT 2026 gold-reference captures (owner's recording + screenshots, 2026-07-22)

Evolved Google Translate (iOS) UI — the D-0 north-star's CURRENT form (BUILD_ROADMAP "GT gold-reference capture" item).

## Home hub (owner SS, 8:17 frame)
- Top bar: bookmark/Saved (left) · centered "Google Translate" wordmark · profile avatar (right)
- Huge "Enter text" placeholder + handwriting icon; big quiet canvas (no headline, no cards)
- Bottom: language chips [English ⇄ Sinhala] → action row: "Live translate" · **center primary MIC (large blue circle)** · "Camera" (labeled buttons)
- **NO bottom navigation bar on phone** — GT uses a hub model: everything 1 tap from Home (Saved icon, avatar→account/settings, mic/camera/live). **Tranzlate deliberately deviates here** (owner, D-5 rev.2 / issue #26): our phone build adds a **persistent bottom nav (Home/Chat/Camera)** — an intentional, owner-approved departure from GT, in the same class as the explicit-Translate-button deviation (DECISIONS D-0 / C-2). The rest of GT's hub (quiet canvas, top-bar Saved/avatar) still informs our design.

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
Mode chip (Automatic ▾) + metered "15/20 today" counter + explicit send for Advanced AI (C-2) · Settings/Paywall entries (avatar menu) · white-label branding · **persistent bottom nav (Home/Chat/Camera) — the D-5 rev.2 deviation from GT's no-phone-bottom-bar hub.**
**Spec reconciliation (updated 2026-07-24, issue #26):** DECISIONS **D-5 rev.2** now records a **persistent bottom nav** on Compact (Home/Chat/Camera) — a deliberate, owner-approved deviation from GT's no-phone-bottom-bar hub (precedent: the explicit-Translate-button deviation, D-0/C-2), **not** a GT-authoritative pattern. CLEAN_ROOM_UX §2/§3, spec-01 §9, DESIGN_SYSTEM §9 and DESIGN_TOKENS §7 are aligned to rev.2. Rail/drawer stays for Medium/Expanded (C-13).

Frames: gt2026-live-typing.jpg · gt2026-result-session.jpg · gt2026-definitions.jpg · gt2026-long-text.jpg (from 61.6s video). Internal design reference only — not shipped assets.

> Source screen-recording videos are kept OUT of git (size) — owner retains originals; extracted frames + screenshots here are the reference.
