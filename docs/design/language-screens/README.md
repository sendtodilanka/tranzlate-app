# Offline Translator — Language screens spec

`Offline Translator - Language Screens Spec.html` — open in any browser. Self-contained: fonts, icons and frame code are inlined, so it works offline and can be shared as one file.

## What is inside

1. **Translate from · phone portrait** — 15a (A–Z rail, the carried-through design) and 15b (segmented filter alternative), light + dark.
2. **Translate to · phone portrait** — 16a, light + dark.
3. **First run, no packs** — 18a phone (list + download confirm sheet), 18b foldable.
4. **Adaptive layouts** — 17a landscape phone 892×412, 17b foldable 760×812, 17c tablet portrait 800×1280, 17d tablet landscape 1280×800; each drawn for both From and To.
5. **Sheets** — ten cases (mobile data, no space, failed download, remove, remove-in-use, offline, detect offline, no offline voice, already-the-source, how it works), light + dark.
6. **Snackbars and Manage packs** — 20a five snackbars (download started, ready, removed with "Download again", failed, waiting for Wi-Fi), 20b Manage packs on phone with the storage-hygiene nudge, 20c pack actions list sheet, 20d Manage packs at 1280×800 as list-detail, 20e the Free up space cleanup sheet.

Every frame is at real device size — measure directly off the page.

## The three rules

- No flags. An ISO-code avatar whose fill states whether the pack is on the device.
- Every row states offline status and pack size.
- Search is a permanent field, never an icon that hides it.

## Tokens (Material 3, Google Blue)

Light — primary `#0b57d0`, primary-container `#d3e3fd`, on-primary-container `#0842a0`, surface `#f8fafd`, container `#e9eef6`, on-surface `#1f1f1f`, variant `#444746`, outline `#747775`, error `#b3261e`, error-container `#f9dedc`.

Dark — surface `#131314`, container `#1e1f20`, high `#2d2f31`, primary `#a8c7fa`, primary-container `#0842a0`, on-primary-container `#d3e3fd`, on-surface `#e3e3e3`, variant `#c4c7c5`, error `#f2b8b5`, error-container `#8c1d18`.

Metrics — list rows 56–60dp, compact rows 48dp, icon buttons 48dp, sheet actions 48dp, corner radius = half the row height, 8dp spacing scale, type from Roboto Flex (22sp app bar, 16sp row, 13.5sp body, 12sp overline).


## Rev 3 — reconciled with the engineering brief

Every figure the app cannot source has been removed from the drawings, per `DESIGNER-BRIEF.md`.

**Library counts are real:** 194 languages in the picker, 59 downloadable as offline packs, 135 online only. On-device counters count against 59, never 194.

**Six row states, no more:** selected · on device · downloading · downloadable · online only · failed. The 135 online-only languages carry the `ONLINE ONLY` chip in place of a download action; a failed row states its cause and offers Retry.

**Removed, because no API can back it:**

| Removed | Replaced with |
|---|---|
| Per-row pack size ("38 MB") | Downloaded rows say "On device". One honest range ("usually 20–50 MB") appears only on the download confirm. |
| Download percentage, "15 MB left" | Indeterminate progress — the row says "Downloading…". |
| Pause, queue, "Move to front", resume | Nothing. ✕ is labelled as stopping and removing; a failure offers Retry, never Resume. |
| Pack version, changelog, update available / required (old sheets 19k, 19l) | Dropped entirely — no version API exists, so the sheets had no trigger. |
| "Update finished" snackbar | Dropped for the same reason. |
| "Pack removed · Undo" | "Spanish removed · Download again" — the pack is off the disk, so the action says what it really does and re-enters the mobile-data gate. |
| Per-app storage split ("Other apps 58%") | Aggregate only: total used by packs, and free space. |
| "takes about 40 seconds" | Nothing — duration is unknowable. |

**Both owner questions are now settled, and the screens follow:**

- **There is no free pack limit — offline packs are unlimited and free.** Sheet 19e is cut and no screen counts slots. Pro keeps selling cloud volume, the higher-quality engine and the extra modalities.
- **No per-row sizes.** Downloaded rows say "On device"; the one sanctioned figure is the "usually 20–45 MB" range on the download confirm.

**The instinct behind the old ceiling is now storage hygiene, not a paywall** — ML Kit itself advises against keeping many models on a device. So Manage packs gains a nudge for packs unused for months, and **20e Free up space** is the sheet it opens: only stale packs listed, both pre-selected, and the copy states that downloading them again is free. Rows there read "not used since April" — app-recorded usage, not a platform figure.

**The ✕ on a downloading row is labelled "Stop download and remove"** and never "Cancel" — there is no pause and no resume.

**Kept, because the platform supports it:** the offline-voice speaker mark (`Voice.isNetworkConnectionRequired`), aggregate storage meters, saved-phrase counts per language, keyboard/locale suggestions on first run, and all four adaptive layouts.

Sheets are now ten (19a, 19b, 19d, 19f, 19g, 19h, 19i, 19j, 19m, 19n) and snackbars five.

## Sheet or snackbar

If the user still has something to decide it is a bottom sheet. If the answer is already settled and only needs reporting, it is a snackbar with a single action — 48dp minimum, 4dp corner, inverse surface, 8dp side margins.

## Not drawn yet

Search-active results · Conversation, camera and phrasebook screens · settings · Pro purchase and restore · permission denials (mic, camera, storage) · download progress notification · search-active results state · RTL mirror for Arabic and Hebrew · large-font and TalkBack passes · motion spec for the sheets, rail and pane transitions.
