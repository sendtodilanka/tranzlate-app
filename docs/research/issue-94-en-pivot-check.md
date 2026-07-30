# Research — issue #94 (N4): MLKit EN-pivot needs NO extra English model

Read-only record. Experiment run 2026-07-30 on the Resizable AVD (prod build).

## Question (engines-phase follow-up N4)

MLKit translates non-English pairs by pivoting through English. Does a
fr→de translation need a separate English model beyond the fr and de packs?

## Setup + result

- Downloaded model set verified on disk: `no_backup/com.google.mlkit.
  translate.models/` = `de_en` + `en_fr` ONLY (MLKit packs are always X↔en
  pairs; there is no standalone `en` model).
- Airplane state: wifi AND data disabled (`svc`), verified before launch.
- App pair set French → German via the picker; composer input
  "Bonjour le monde" → Translate.
- **Result: "Hallo Welt" — success, fully offline.** Proof by construction:
  no network path existed, so the answer came from MLKit's on-device pivot
  (fr→en→de) using exactly the two downloaded packs.

## Conclusion

N4 CLOSED: the fr+de packs suffice; the EN leg ships inside each pack. No
code change needed; the offline-manager UX ("download the two languages you
need") is already correct. No hidden "also download English" requirement
exists to surface in UI.
