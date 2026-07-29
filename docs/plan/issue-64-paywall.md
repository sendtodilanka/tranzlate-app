---
status: accepted   # owner directive 2026-07-29 ("1 idan piliwalata karagena yanna…"); BUSINESS_MODEL §4-§5 is the owner-locked spec this implements verbatim
issue: 64
title: Paywall vertical + C-11 limit sheet + goal-gradient meter
date: 2026-07-29
author: Claude (Opus 5)
---

# Plan — issue #64

BUSINESS_MODEL is the single source of truth; this batch turns its §4 (paywall screen),
§5 triggers #1 and #5, and the goal-gradient rule into code. Billing stays behind the
NoOp gateway — every purchase/restore surfaces an HONEST failure ("billing isn't
available yet"), never a fake success. Qonversion wiring is a later, separate batch.

## Pieces

1. **`:feature:paywall`** — `PaywallViewModel` (PurchaseFlow + FeatureAccess asks only)
   + `PaywallScreen` per the §4 frame: dismissible ✕ (Play policy), benefit-led bullets,
   Weekly / Monthly / **Yearly pre-selected** (★ + "7-day free trial" + "SAVE ~60%"),
   per-day framing, "Cancel anytime", CTA text follows selection, Restore · Terms ·
   Privacy. Display prices are **placeholder resources** (TODO: store offerings) — the
   app is unreleased and the gateway is NoOp; real prices arrive with Qonversion.
   Already-PRO (or purchase success) auto-dismisses.
2. **Trigger #5**: Home's `TokenProChip` navigates to `PaywallNavKey` (was a snackbar).
3. **Trigger #1 — C-11 sheet**: `TextUiState.Limit` face additionally raises a
   **dismissible `ModalBottomSheet`** (`tt_text_limit_sheet`) — in place, composer
   stays under it, free engines keep working. Quota copy vs not-entitled copy differ
   (PR-60 lens N2's upgrade CTA lands here). Dismiss = "Not now"; CTA = paywall.
4. **Goal-gradient meter (§5)**: when an AI-quality result (`ONLINE_CLOUD_NLP`) is on
   screen and the user is FREE, the composer shows "AI translations: N/5 left today"
   from `UsagePolicy.remaining` — the meter that makes trigger #1 expected, not a
   surprise. `TextViewModel` gains read-only asks (UsagePolicy · FeatureAccess ·
   RemoteConfigSource).
5. **a11y**: the new limit surfaces get real `liveRegion` semantics (starts paying down
   the recorded unwired-liveRegion debt).
6. `FakeRemoteConfig` moves into `:core:testing` (third local duplicate avoided).

## Acceptance
Unit: paywall selection/purchase/restore paths (NoOp → honest failure event), meter
exposure mapping, sheet state per Limit kind. Device: paywall reachable via the Pro
chip, screenshot evidence; limit sheet exercised via previews + VM tests (its trigger
is honestly unreachable in the shipped AUTO-only build). Gates: full suite · lint ·
fake+prod assemble · CI · cross-model lens (billing-adjacent per Rule 5).
