# Plan — issue #94: engines follow-ups (N4 closed + primaryCause skip-masking)

status: accepted
(accepted basis: owner standing rules — autonomous B-list + debate before
implementation. Scaled per sub-feature size: N4 is a pure experiment (no
design); the primaryCause semantics ran a COMPRESSED cross-model debate —
one Opus agent advocating all options then ruling adversarially, grounded in
the actual consumer/reachability map it verified from source.)

## N4 — EN-pivot: CLOSED by experiment, no code

Airplane mode + only `de_en`+`en_fr` packs → "Bonjour le monde" → "Hallo
Welt" fully offline. The EN pivot leg ships inside each pack; there is no
hidden "download English too" requirement to surface. Record:
docs/research/issue-94-en-pivot-check.md.

## primaryCause — the ruling (Option A of A/B/C)

Grounding the debate verified: only ONE production consumer
(TextViewModel → error face copy); distinct copy today = OFFLINE +
UNSUPPORTED_PAIR (others fall to generic — but engines-phase copy for
MODEL_NOT_DOWNLOADED/TIMEOUT is promised, which is when a skip-tail mask
turns user-visible); `SKIPPED_SOURCE_UNKNOWN` is always attempts[0] (head);
`SKIPPED_NO_QUOTA` only appends after the online pre-flight passes (so the
reachable mask is `[real online failure, SKIPPED_NO_QUOTA]`); ALL-skip is
reachable (AUTO-undetected + kill-switched GOT + no quota) so a fallback is
mandatory.

- **Rejected C (keep):** leaves a mask that goes user-visible the moment
  engines-phase copy lands.
- **Rejected B (actionability ranking):** hand-maintained total order,
  re-justified on every new cause, can actively mislead, most golden churn.
- **Adopted A:** `primaryCause = attempts.lastOrNull { !it.cause.isSkip }
  ?.cause ?: attempts.last().cause` + exhaustive `AttemptCause.isSkip`
  (a new enum entry forces review at the `when`). Zero existing-test churn;
  four new model tests (single-real / skip-head / skip-tail / all-skip).
