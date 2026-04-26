---
phase: 02-tech-debt-parity-coverage-expansion
plan: 02-fixture-expansion
subsystem: parity-harness + test-fixtures
tags: [test-11, test-12, test-13, overwrite-map, prediction-mode, assertparity-tightening, oracle-pinning]
type: execute
status: complete
completed: 2026-04-25
duration: ~70min (incl. ~25min waiting on transient 1Password signing-agent failure)
one_liner: "5 new oracle-pinned fixtures + assertParity tightening (overwriteReason load-bearing); 35 fixtures green across 3 modes."
requirements_completed: [TEST-11, TEST-12, TEST-13]
key_files:
  created:
    - src/test/resources/fixtures/edge/randomtxt.bin
    - src/test/resources/fixtures/edge/randomtxt.bin.expected.json
    - src/test/resources/fixtures/edge/medium-confidence-1.bin
    - src/test/resources/fixtures/edge/medium-confidence-1.bin.expected.json
    - src/test/resources/fixtures/edge/medium-confidence-2.bin
    - src/test/resources/fixtures/edge/medium-confidence-2.bin.expected.json
    - src/test/resources/fixtures/edge/best-guess-1.bin
    - src/test/resources/fixtures/edge/best-guess-1.bin.expected.json
    - src/test/resources/fixtures/edge/best-guess-2.bin
    - src/test/resources/fixtures/edge/best-guess-2.bin.expected.json
  modified:
    - src/test/java/dev/jcputney/magika/parity/UpstreamParityIT.java
    - src/test/resources/fixtures/README.md
decisions:
  - topic: "Three-Magika-singleton harness shape (RESEARCH §Pattern 5 option (a))"
    decision: "@BeforeAll constructs MAGIKA_HIGH (renamed from MAGIKA), MAGIKA_MEDIUM, MAGIKA_BEST via Magika.builder().predictionMode(...).build(); each mode owns its own @TestFactory; prefix-based fixture routing (medium-confidence-* / best-guess-*) keeps mode-fixture pairings explicit at the filesystem level."
    rationale: "Native session creation runs three times at suite start (~795ms + 21ms + 18ms measured) — one-time cost is acceptable for the explicit per-mode separation that filesystem-based routing provides. Avoids parameterizing one factory with mode metadata, which would couple sidecar shape to test-engine internals."
  - topic: "assertParity tightening commit ordering (RESEARCH §Pitfall 3)"
    decision: "assertParity tightening (T03) lands in a dedicated commit AFTER the fixtures (T02) — separate commits, not one combined feat+test."
    rationale: "Any latent v0.1 reason-mismatch would surface in isolation against unchanged v0.1 fixtures. None did: all 30 v0.1 fixtures pass the new reasonOk + dlReasonOk gate without modification (the field was already in every sidecar; the harness was just ignoring it). Bisect-friendly if a regression had appeared."
  - topic: "Open Question #2 — dl.overwriteReason invariant assertion"
    decision: "Assert both dl.overwriteReason (dlReasonOk) AND output.overwriteReason (reasonOk) — strict equality on both."
    rationale: "The dl.overwriteReason by-construction-NONE invariant is also enforced, not just output-side. Both fields were already present in every sidecar; contract was already documented. Enforcing both costs zero additional sidecar work."
  - topic: "TEST-11 fixture-byte recipe character-set discovery"
    decision: "Use deterministic Random(20260425) over (string.ascii_letters + string.digits) only — narrow alnum-only character set, length 256."
    rationale: "Probe revealed full string.printable lands as randombytes (overwrite_map → unknown), NOT randomtxt. Only the narrow alnum-only set lands the model on randomtxt. Bytes are pinned with a do-not-regenerate caveat in fixtures/README.md, matching the existing random-bytes.bin pattern. Documented in README's flags/caveats section to prevent future contributors from 'fixing' the recipe."
  - topic: "TEST-12 / TEST-13 fixture selection: label diversity + divergence verification"
    decision: "Pick 4 distinct dl labels (ignorefile, ocaml, dart, csv) probed under both HIGH and target-mode invocations against the upstream Python oracle to verify per-fixture divergence before pinning bytes."
    rationale: "Each fixture's HIGH-mode result is the dl label falling back to txt LOW_CONFIDENCE; target-mode (MEDIUM or BEST_GUESS) keeps the dl label with reason NONE — the divergence the test is meant to prove. Label diversity guards against single-class regression masking the prediction-mode divergence."
  - topic: "Parity-bar non-negotiable (CLAUDE.md held)"
    decision: "1e-4 score tolerance unchanged; reasonOk and dlReasonOk are strict equality; no @Disabled, no softening; failure-message format gained a [reason mismatch] banner for at-a-glance debugging but the gate itself is exact."
    rationale: "The banner is a diagnostic aid (similar to the existing failure reproducer text) — it does not weaken any assertion. Adding diagnostics without weakening gates is the project pattern (cf. UpstreamParityIT line 277 reproducer style)."
commits:
  - ff9f440 test(02-02) wire UpstreamParityIT for three prediction modes (TEST-11/12/13 harness)
  - e1ec3c5 test(02-02) add 5 oracle-pinned fixtures for TEST-11/12/13
  - df8e8ad test(02-02) tighten assertParity with output.overwriteReason + dl.overwriteReason equality (TEST-11 load-bearing)
metrics:
  fixtures_v0_1: 30
  fixtures_v0_2_added: 5
  fixtures_total: 35
  parity_test_factories: 3 (HIGH / MEDIUM / BEST)
  parity_dynamic_tests: 35 (31 HIGH incl. randomtxt + 2 MEDIUM + 2 BEST)
  parity_named_regression_tests: 7
  upstream_parity_it_total: 42
  unit_tests: 102
  failsafe_tests: 65
  total_tests_full_suite: 167
  parity_disagreements: 0
  reason_mismatches_found_on_v0_1: 0
  oracle_pin_unchanged: true
  next_plan: phase-03 (Public API Ergonomics — REF-01..04)
---

# Phase 2 Plan 2: Fixture Expansion Summary

Five new oracle-pinned parity fixtures land under `src/test/resources/fixtures/edge/`, expanding `UpstreamParityIT` from 30 default-HIGH fixtures into 35 fixtures across all three `PredictionMode` paths (HIGH / MEDIUM / BEST_GUESS) plus the previously-uncovered `randomtxt → txt` overwrite-map branch. The harness gains three Magika singletons (`MAGIKA_HIGH`, `MAGIKA_MEDIUM`, `MAGIKA_BEST`) and a tightened `assertParity` that enforces strict equality on `output.overwriteReason` and `dl.overwriteReason` — making TEST-11's OVERWRITE_MAP signal load-bearing in parity rather than diagnostic-only. All 35 fixtures green; zero latent v0.1 reason-mismatches surfaced. Oracle pin unchanged at `magika @ 363a44183a6f300d5d7143d94a19e6a841671650` (1.0.2).

## Requirements Closed (per requirement)

### TEST-11 — `randomtxt → txt` overwrite-map oracle (CLOSED — commits e1ec3c5 + df8e8ad)

**What it adds:** The second of two `overwrite_map` entries in `config.min.json` (the first, `randombytes → unknown`, was covered in v0.1 by `edge/random-bytes.bin`). `randomtxt` is the model output for high-entropy UTF-8-valid printable text with no learnable structure; the overwrite-map rewrites it to `txt` with `output.overwriteReason: OVERWRITE_MAP`.

**Fixture bytes:** `edge/randomtxt.bin` (256 bytes), generated from `random.Random(20260425).choice(string.ascii_letters + string.digits)` — i.e. uniform-random alphanumerics. The bytes are pinned with a do-not-regenerate caveat. **Fixture-authoring discovery:** the model is sensitive to character-set entropy. Full `string.printable` (which includes whitespace + punctuation) lands as `randombytes` (overwrite-map → `unknown`); only the narrow alnum-only set lands as `randomtxt`. This is documented in the README's flags/caveats section to prevent future contributors from "fixing" the recipe.

**Sidecar:** `dl.label="randomtxt"`, `output.label="txt"`, `output.overwriteReason="OVERWRITE_MAP"`, score `0.9418603777885437`. Oracle-pinned.

**Why it's load-bearing:** Without the new `reasonOk` check (T03), the same bytes whose `dl.label="randomtxt"` and `output.label="txt"` would be parity-indistinguishable from a native txt fixture. The OVERWRITE_MAP signal — the actual coverage gain of the fixture — only counts in parity once the harness asserts on it.

### TEST-12 — MEDIUM_CONFIDENCE-mode oracle, ≥2 fixtures (CLOSED — commits e1ec3c5 + df8e8ad)

**What it adds:** Two fixtures whose dl label is one of the 12 per-type-overridden classes (`markdown 0.75, ignorefile 0.95, latex 0.95, ocaml 0.9, pascal 0.95, r 0.9, rst 0.9, sql 0.9, tsv 0.9, zig 0.9, crt 0.9, handlebars 0.9`) and whose score lands in `[0.5, per_type_threshold)` — the only band where MEDIUM_CONFIDENCE diverges from HIGH_CONFIDENCE.

| Fixture | Bytes | dl.label | score | MEDIUM output | HIGH output (divergence) |
|---|---|---|---|---|---|
| `medium-confidence-1.bin` | `"# comment\nfoo.bak\n"` (18 B) | `ignorefile` | 0.7463 | `ignorefile NONE` | `txt LOW_CONFIDENCE` |
| `medium-confidence-2.bin` | `"let f x = x + 1;;\nf 5;;\n"` (24 B) | `ocaml` | 0.6267 | `ocaml NONE` | `txt LOW_CONFIDENCE` |

Both candidates were probed under both `Magika()` (HIGH) and `Magika(prediction_mode=PredictionMode.MEDIUM_CONFIDENCE)` against the pinned oracle to confirm divergence before pinning the bytes. Score in `[0.5, per_type_threshold)` is the necessary-and-sufficient condition for MEDIUM-vs-HIGH divergence (per `magika.py:610` — MEDIUM_CONFIDENCE ignores the per-type map and uses the scalar `medium_confidence_threshold = 0.5`).

### TEST-13 — BEST_GUESS-mode oracle, ≥2 fixtures (CLOSED — commits e1ec3c5 + df8e8ad)

**What it adds:** Two low-score fixtures whose dl label is meaningful but whose score is well below 0.5 — under HIGH or MEDIUM these would fall back to `txt LOW_CONFIDENCE`, but under BEST_GUESS (threshold = 0.0) they retain the dl label with reason NONE.

| Fixture | Bytes | dl.label | score | BEST output | HIGH output (divergence) |
|---|---|---|---|---|---|
| `best-guess-1.bin` | `"void main(){}\n"` (14 B) | `dart` | 0.1608 | `dart NONE` | `txt LOW_CONFIDENCE` |
| `best-guess-2.bin` | `"192.168.0.1\n"` (12 B) | `csv` | 0.1888 | `csv NONE` | `txt LOW_CONFIDENCE` |

Per RESEARCH §Pitfall 5: "any low-score fixture works" for BEST_GUESS divergence (threshold = 0.0). The 4 chosen labels (`ignorefile`, `ocaml`, `dart`, `csv`) span 4 distinct training classes — label diversity in case the model later regresses on one specific class.

## Architecture (UpstreamParityIT structure after Plan 02-02)

```
@BeforeAll
  MAGIKA_HIGH   = Magika.builder().predictionMode(HIGH_CONFIDENCE).build()    // default mode
  MAGIKA_MEDIUM = Magika.builder().predictionMode(MEDIUM_CONFIDENCE).build()  // TEST-12
  MAGIKA_BEST   = Magika.builder().predictionMode(BEST_GUESS).build()         // TEST-13

@TestFactory parityHighConfidenceFixtures()
  -> all fixtures EXCEPT medium-confidence-* / best-guess-*  -> MAGIKA_HIGH
     (31 dynamic tests: 30 v0.1 + randomtxt.bin)

@TestFactory parityMediumConfidenceFixtures()
  -> medium-confidence-* fixtures only -> MAGIKA_MEDIUM
     (2 dynamic tests)

@TestFactory parityBestGuessFixtures()
  -> best-guess-* fixtures only -> MAGIKA_BEST
     (2 dynamic tests)

7 named @Test methods (identifyBytes / identifyStream / CR-01 / CR-02 regressions) -> MAGIKA_HIGH

assertParity(fixture, actual):
  dlOk        = expected.dl.label == actual.dl.label
  outOk       = expected.output.label == actual.output.label
  scoreOk     = |expected.score - actual.score| < 1e-4
  reasonOk    = expected.output.overwriteReason == actual.output.overwriteReason.name()  -- T03 NEW
  dlReasonOk  = expected.dl.overwriteReason     == actual.dl.overwriteReason.name()      -- T03 NEW
  if all five: return
  else: AssertionError with "[reason mismatch]" banner if reason drift
```

## assertParity Tightening — Before/After Diff

**Before (post-T01, pre-T03):**
```java
boolean dlOk = expected.dl().label().equals(actDlLabel);
boolean outOk = expected.output().label().equals(actOutLabel);
boolean scoreOk = Math.abs(expected.score() - actScore) < SCORE_TOLERANCE;

if (dlOk && outOk && scoreOk) {
  return;
}
// Failure message printed reasons but never asserted on them.
```

**After (T03):**
```java
boolean dlOk = expected.dl().label().equals(actDlLabel);
boolean outOk = expected.output().label().equals(actOutLabel);
boolean scoreOk = Math.abs(expected.score() - actScore) < SCORE_TOLERANCE;

String expectedOutReason = expected.output().overwriteReason();
String actualOutReason = actual.output().overwriteReason().name();
boolean reasonOk = expectedOutReason.equals(actualOutReason);

String expectedDlReason = expected.dl().overwriteReason();
String actualDlReason = actual.dl().overwriteReason().name();
boolean dlReasonOk = expectedDlReason.equals(actualDlReason);

if (dlOk && outOk && scoreOk && reasonOk && dlReasonOk) {
  return;
}
// Failure message gains "[reason mismatch]" banner; per-prediction reason inlined.
```

**Latent reason-mismatch survey (post-T03 verify gate):** Zero. All 30 v0.1 fixtures pass the new gate without sidecar regeneration. RESEARCH §Pattern 4's "free tightening" prediction held. The harness was reporting reasons in failure messages all along; T03 just made the gate match the diagnostic.

## Verify Gate Result (T03 close)

`mvn -B -ntp verify` — **GREEN** at commit `df8e8ad`:

| Suite | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| Surefire (unit + ArchUnit) | 102 | 0 | 0 | 0 |
| Failsafe (IT incl. parity) | 65 | 0 | 0 | 0 |
| **Total** | **167** | **0** | **0** | **0** |
| Spotless | clean | — | — | — |

**UpstreamParityIT** breakdown: **42 dynamic + named tests** = 35 fixture-driven (31 HIGH + 2 MEDIUM + 2 BEST) + 7 named regression methods (identifyBytes / 2× identifyStream tail-signal / 3× CR-01 small-file / 1× CR-02 stripped-content). All 42 green.

**ConcurrencyIT, MagikaApiIT, OnnxInferenceEngineIT, MagikaLoggingTest** all green — D-11 three-event log contract preserved through harness restructuring (each Magika instance still emits exactly one INFO-on-load + one INFO-on-close).

## Per-fixture Verification (post-T03)

| Fixture | Mode | Factory | dl | output | reason | score | Status |
|---|---|---|---|---|---|---|---|
| `edge/randomtxt.bin` | HIGH | parityHighConfidenceFixtures | randomtxt | txt | OVERWRITE_MAP | 0.9418603777885437 | green |
| `edge/medium-confidence-1.bin` | MEDIUM | parityMediumConfidenceFixtures | ignorefile | ignorefile | NONE | 0.746256411075592 | green |
| `edge/medium-confidence-2.bin` | MEDIUM | parityMediumConfidenceFixtures | ocaml | ocaml | NONE | 0.6267337203025818 | green |
| `edge/best-guess-1.bin` | BEST_GUESS | parityBestGuessFixtures | dart | dart | NONE | 0.16084469854831696 | green |
| `edge/best-guess-2.bin` | BEST_GUESS | parityBestGuessFixtures | csv | csv | NONE | 0.18882256746292114 | green |

## Deviations from Plan

### Auto-fixed Issues

None. Plan executed exactly as written — all three tasks landed in their planned waves with no Rule 1/2/3 deviations needed. The T03 verify gate prediction (zero latent v0.1 reason-mismatches) held; no parity bug investigation was triggered.

### Authentication / Environment Gates Encountered

**1Password SSH-signing agent persistent failure between T02 generation and T02 commit.**

- **Type:** environmental gate — not a bug, not a fixture issue.
- **Symptoms:** `op-ssh-sign -Y sign` returned `1Password: agent returned an error` consistently for ~15 minutes across ~10 retry attempts. The SSH-listing endpoint (`ssh-add -L`) worked; only the signing endpoint failed. Earlier in the session the 1Password app was caught in `--just-updated --should-restart` state — likely the proximate cause.
- **Resolution:** Per the parallel-execution protocol, the executor returned a structured checkpoint (refused to bypass signing — `--no-gpg-sign` is forbidden). The user restarted the 1Password app; the orchestrator's re-probe confirmed signing recovery; this executor was re-spawned and committed T02 + T03 with signatures intact. No work was lost (no worktree force-removal — Wave 1 fixtures were staged on `main` directly and survived the gap).
- **Plan 02-01 had hit a similar transient that resolved on first retry; this one was longer-lived but ultimately the same class of failure.**

## Threat Model Verification

Plan 02-02's `<threat_model>` section assigned `mitigate` dispositions to T-02-07 (malicious sidecar JSON), T-02-08 (tampered sidecar bypass), T-02-10 (latent v0.1 reason-mismatch silently passing), and T-02-12 (randomtxt seed silently changing). Verification:

- **T-02-07:** `FixtureLoader.discoverFixtures` continues to walk `FIXTURES_ROOT` only; no `..` traversal possible. Five new sidecars deserialize cleanly into `ExpectedResult` with no polymorphic typing exposure. Verified by `FixtureLoaderOrphanTest` (still green) + the new fixtures parsing correctly.
- **T-02-08:** `assertParity` now strict-equality-checks both `output.overwriteReason` AND `dl.overwriteReason`. A future tampered sidecar that flipped the reason field without flipping the label would fail loudly with the "[reason mismatch]" banner. Verified by the T03 verify gate finding zero existing drift.
- **T-02-10:** Latent v0.1 reason-mismatch survey: zero. The previously-silent drift channel is closed.
- **T-02-12:** `fixtures/README.md` "do not regenerate" caveat now lists `randomtxt.bin` alongside `random-bytes.bin`. Bytes are deterministic + git-tracked; any byte-level change is visible in `git diff`.

## Threat Flags

None. Phase 2 introduced no new attack surface — internal test expansion + assertion tightening only. No new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries.

## Stub Tracking

None. Every new artifact is wired end-to-end:
- All 5 fixtures + sidecars are discovered by `FixtureLoader.discoverFixtures` and exercised by the appropriate `@TestFactory`.
- All 5 sidecars carry the oracle pin; oracle SHA grep returns 5/5 hits.
- README provenance rows + non-default-mode regeneration recipe + do-not-regenerate caveat all present and verified by grep.
- `assertParity` reasonOk + dlReasonOk are wired into the gate condition (verified by `grep -F "&& reasonOk"`).
- `MAGIKA_MEDIUM` and `MAGIKA_BEST` are constructed in `@BeforeAll`, closed in `@AfterAll`, and consumed by their dedicated `@TestFactory` methods (no orphan fields).

## Self-Check: PASSED

**Files claimed created in this SUMMARY — exist:**
- `src/test/resources/fixtures/edge/randomtxt.bin` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/randomtxt.bin.expected.json` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/medium-confidence-1.bin` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/medium-confidence-1.bin.expected.json` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/medium-confidence-2.bin` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/medium-confidence-2.bin.expected.json` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/best-guess-1.bin` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/best-guess-1.bin.expected.json` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/best-guess-2.bin` ✓ (commit e1ec3c5)
- `src/test/resources/fixtures/edge/best-guess-2.bin.expected.json` ✓ (commit e1ec3c5)

**Commits claimed in this SUMMARY — exist:**
- `ff9f440`, `e1ec3c5`, `df8e8ad` — all present in `git log a1b6587..HEAD --oneline`.

**Oracle pin verification:**
```bash
grep -lF "363a44183a6f300d5d7143d94a19e6a841671650" src/test/resources/fixtures/edge/*.expected.json | wc -l
# 17  (12 v0.1 edge fixtures + 5 new — all carry the pin)
```

**Sidecar count delta:**
```bash
find src/test/resources/fixtures/ -name '*.expected.json' | wc -l
# 35  (was 30 at v0.1 close)
```

**Suite green:**
- Surefire 102/102, Failsafe 65/65, Spotless clean. UpstreamParityIT specifically: 42/42.

**No softening:** `! grep -rn '@Disabled' src/test/java/` returns empty. `SCORE_TOLERANCE = 1e-4` unchanged.

**Acceptance grep note:** The plan's literal acceptance check `grep -F "do not regenerate"` returns 0 hits because the README uses "Do **NOT** regenerate" / "Do NOT regenerate" (markdown-emphasized capitals). The semantic intent — a do-not-regenerate caveat for both `random-bytes.bin` and `randomtxt.bin` — is satisfied: case-insensitive grep finds 3 occurrences (1 inline in the provenance row for `randomtxt.bin`, 2 in the dedicated flags/caveats blocks for `random-bytes.bin` and `randomtxt.bin`). No correction made — capitalising NOT for emphasis is the surrounding markdown style.

## Next Plan

Phase 02 closes with this plan. The orchestrator advances to Phase 03 (Public API Ergonomics — REF-01 `MagikaResult.Status` enum, REF-02 `identifyPaths(List<Path>)` batch, REF-03 `module-info.java` JPMS, REF-04 lazy `OrtSession` init).
