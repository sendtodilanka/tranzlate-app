# Offline Translator — Language screens spec

`Offline Translator - Language Screens Spec.html` — open in any browser. Self-contained: fonts, icons and frame code are inlined, so it works offline and can be shared as one file.

## What is inside

1. **Translate from · phone portrait** — 15a (A–Z rail, the carried-through design) and 15b (segmented filter alternative), light + dark.
2. **Translate to · phone portrait** — 16a, light + dark.
3. **First run, no packs** — 18a phone (list + download confirm sheet), 18b foldable.
4. **Adaptive layouts** — 17a landscape phone 892×412, 17b foldable 760×812, 17c tablet portrait 800×1280, 17d tablet landscape 1280×800; each drawn for both From and To.
5. **Sheets** — 19a–19n, fourteen cases (mobile data, no space, queued, interrupted, free limit, remove, remove-in-use, offline, detect offline, no offline voice, update available, update required, already-the-source, how it works), light + dark.

Every frame is at real device size — measure directly off the page.

## The three rules

- No flags. An ISO-code avatar whose fill states whether the pack is on the device.
- Every row states offline status and pack size.
- Search is a permanent field, never an icon that hides it.

## Tokens (Material 3, Google Blue)

Light — primary `#0b57d0`, primary-container `#d3e3fd`, on-primary-container `#0842a0`, surface `#f8fafd`, container `#e9eef6`, on-surface `#1f1f1f`, variant `#444746`, outline `#747775`, error `#b3261e`, error-container `#f9dedc`.

Dark — surface `#131314`, container `#1e1f20`, high `#2d2f31`, primary `#a8c7fa`, primary-container `#0842a0`, on-primary-container `#d3e3fd`, on-surface `#e3e3e3`, variant `#c4c7c5`, error `#f2b8b5`, error-container `#8c1d18`.

Metrics — list rows 56–60dp, compact rows 48dp, icon buttons 48dp, sheet actions 48dp, corner radius = half the row height, 8dp spacing scale, type from Roboto Flex (22sp app bar, 16sp row, 13.5sp body, 12sp overline).

## Not drawn yet

Snackbars (download started, pack removed with Undo, update finished) and the **Manage packs** page the sheets point at.
