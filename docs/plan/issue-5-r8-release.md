# Plan — issue #5: R8 on the release build (debate-ruled)

status: accepted
(accepted basis: owner standing rules — autonomous B-list + debate. Compressed
cross-model debate: options minimal / comprehensive / defer → ruling =
minimal config + verification belt, no hand-keep sprawl.)

## The ruling

- `release { isMinifyEnabled = true; isShrinkResources = true }` with the
  default optimize file + project rules.
- ONE justified project rule up front: first-party persisted-enum pin
  (Engine crosses the DB app-update boundary as name()→valueOf; FeatureToggle
  csv likewise). Everything else rides library consumer rules on purpose.
- NO signingConfig committed — per-brand keystores land with the
  Qonversion/ads/release batch. `assembleTranzlateProdRelease` output is
  unsigned; smoke signs it locally with the debug keystore.
- CI gate: already in place BY CONSTRUCTION — CI runs `gradlew build`, which
  assembles tranzlateProdRelease, so R8 config errors gate every PR.
- One-time device smoke (Rule 6): DB history read (Engine.valueOf) · GOT
  online translate (JSON DOM parse) · MLKit offline translate · settings
  theme persist (DataStore) · the offline-manager screen (DI construction).

## What the smoke caught (the reason it exists)

First release run: Home, GOT translate, History, theme all PASSED — then the
Offline screen CRASHED the app at first MLKit DI touch:
`MlKitModelStore.<init>` → `RemoteModelManager.getInstance()` → NPE
(`getClass()` on a null component). Mapping-retraced. The AAR consumer rules
HAD kept the registrars un-renamed (verified in mapping.txt) — the break is
an internal surface those rules miss under AGP's R8 full mode. Fix: a
namespace keep `-keep class com.google.mlkit.** { *; }` — verified working
(offline screen renders, French downloads, airplane translate "See you
tomorrow"→"À demain" offline). NARROWING the keep is recorded follow-up work
for the Qonversion/ads batch (which reopens rules anyway).

## Deferred to the Qonversion/ads/release batch

Per-brand signing keystores · Qonversion/AdMob rule verification · GCT key
path under R8 · mlkit-keep narrowing · Play-ready bundle (AAB) config.
