---
phase: 02-tech-debt-parity-coverage-expansion
plan: 01-debt-closure
subsystem: postprocess + config + inference + parity-harness
tags: [debt-closure, alg-14-gate, archunit-rule, sentinel-relocation, slf4j-warn, sha-caching]
type: execute
status: complete
completed: 2026-04-25
duration: ~25min
one_liner: "Closes v0.1 carry-forward debt: sentinels relocated to ContentTypeInfo (drift fixed), ThresholdPolicy extracted, IN-01..IN-05 closed."
requirements_completed: [DEBT-01, DEBT-02, DEBT-03]
findings_closed: [WR-04, IN-01, IN-02, IN-03, IN-04, IN-05, POST-02]
key-files:
  created:
    - src/main/java/dev/jcputney/magika/postprocess/ThresholdPolicy.java
    - src/test/java/dev/jcputney/magika/postprocess/ThresholdPolicyTest.java
    - src/test/java/dev/jcputney/magika/postprocess/ContentTypeLabelSentinelsTest.java
    - src/test/java/dev/jcputney/magika/inference/onnx/OnnxInferenceEngineCloseTest.java
    - src/test/java/dev/jcputney/magika/parity/ExpectedResultTest.java
    - src/test/java/dev/jcputney/magika/parity/FixtureLoaderOrphanTest.java
  modified:
    - docs/algorithm-notes.md
    - src/main/java/dev/jcputney/magika/config/ContentTypeInfo.java
    - src/main/java/dev/jcputney/magika/postprocess/ContentTypeLabel.java
    - src/main/java/dev/jcputney/magika/postprocess/LabelResolver.java
    - src/main/java/dev/jcputney/magika/Magika.java
    - src/main/java/dev/jcputney/magika/inference/onnx/OnnxModelLoader.java
    - src/main/java/dev/jcputney/magika/inference/onnx/OnnxInferenceEngine.java
    - src/test/java/dev/jcputney/magika/PackageBoundaryTest.java
    - src/test/java/dev/jcputney/magika/inference/onnx/OnnxModelLoaderTest.java
    - src/test/java/dev/jcputney/magika/parity/ExpectedResult.java
    - src/test/java/dev/jcputney/magika/parity/FixtureLoader.java
decisions:
  - "DEBT-01 implementation choice (b): static-final sentinel constants relocated from postprocess.ContentTypeLabel into config.ContentTypeInfo (alongside existing UNDEFINED). Preserves LabelResolverTest isSameAs object identity; keeps ArchUnit rule clean (no per-package exceptions)."
  - "DEBT-02 IN-04 + DEBT-03 ALG-14 land in ONE combined docs commit (6dcf9ad) at the head of the plan. Verified post-hoc by content-anchored ancestor check (git log -S 'ThresholdPolicy class extraction' / git merge-base --is-ancestor)."
  - "DEBT-02 IN-02 SLF4J-only WARN log on close-time OrtException — no MockAppender / logback dep added (CLAUDE.md no-unapproved-deps). Negative log-grep test in OnnxInferenceEngineCloseTest verifies the WARN does NOT fire on healthy close."
  - "DEBT-02 IN-03 Option A: fixture-aware overload overwriteReasonEnum(Path) on ExpectedPrediction. 0-arg overload preserved for back-compat (delegates with null fixture)."
  - "DEBT-02 IN-05 log-WARN over fail-fast: FixtureLoader.discoverFixtures emits SLF4J WARN per orphan, preserving the @TestFactory iterative-fixture-write workflow."
  - "DEBT-01 Rule 1 deviation (sentinel description drift): the new ContentTypeLabelSentinelsTest surfaced v0.1 doc-rot — three hardcoded ContentTypeInfo.{TXT, UNKNOWN, EMPTY} descriptions disagreed with the bundled content_types_kb.json. Per CLAUDE.md non-negotiable + plan T03 STOP-AND-ASK guidance, sentinels were updated to match upstream verbatim; assertions were NOT softened. Drift documented in Javadoc on each constant."
  - "DEBT-02 IN-01 back-compat preserved: load() returns LoadedModel(bytes, sha256) — preferred new entry; loadAndVerify() and computeSha256() retained for existing OnnxModelLoaderTest references."
commits:
  - a562741 test(02-01) scaffold Wave-0 RED tests for DEBT-01/02/03 + 7th ArchUnit rule
  - 6dcf9ad docs(02-01) document ThresholdPolicy extraction + content_types_kb shape cross-ref
  - 3cb17a4 refactor(02-01) consolidate ContentTypeLabel sentinels via ContentTypeInfo (DEBT-01 / WR-04)
  - 57c6f1b refactor(02-01) extract ThresholdPolicy from LabelResolver (DEBT-03 / POST-02)
  - efc93c4 refactor(02-01) close IN-01 (SHA-256 caching) + IN-02 (close-time WARN log) (DEBT-02)
  - a7ec01e refactor(02-01) close IN-03 (ExpectedResult fixture-context) + IN-05 (FixtureLoader WARN) (DEBT-02)
  - 3aaf7c0 fix(02-01) align sentinel descriptions to bundled registry; spotless reformat (DEBT-01 / Rule 1)
metrics:
  archunit_rules_before: 6
  archunit_rules_after: 7
  unit_tests_before: 88
  unit_tests_after: 102
  failsafe_tests: 60
  total_tests: 162
  parity_disagreements: 0
  alg_14_gate_proven: true
  next_plan: 02-02
---

# Phase 2 Plan 1: v0.1 Debt Closure Summary

Surgical closure of v0.1 carried-forward tech debt: WR-04 (DEBT-01), IN-01..IN-05 (DEBT-02), POST-02 (DEBT-03). Wave-0 RED tests scaffolded all five new test classes + the 7th ArchUnit rule before code changes; Wave-1 docs commit (6dcf9ad) anchored the ALG-14 read-before-write gate; Wave-2 production-source edits flipped the failing tests green; Wave-3 verify gate confirmed 30 v0.1 oracle-pinned parity fixtures still byte-for-byte clean. ALG-14 ordering proven by content-anchored ancestor check.

## Findings Closed (per finding)

### WR-04 / DEBT-01 — Hardcoded sentinel `ContentTypeInfo` payloads (FIXED — commits 3cb17a4 + 3aaf7c0)

**Root cause:** v0.1 `postprocess.ContentTypeLabel.java:48-76` constructed `new ContentTypeInfo(...)` ad-hoc for the EMPTY / UNKNOWN / TXT sentinels with hardcoded field values. The hardcodes (a) put production-code construction outside the `config..` package boundary that owns `ContentTypeInfo` rows, and (b) drifted from the bundled `content_types_kb.json`.

**Fix:** Three new `static final ContentTypeInfo` constants live alongside `UNDEFINED` in `config.ContentTypeInfo.java`. `ContentTypeLabel.java` now references the new constants by name (zero `new ContentTypeInfo(...)` calls remain in `postprocess..`). Drop unused `import java.util.List`.

**ArchUnit rule (7th):** `PackageBoundaryTest.contentTypeInfoConstructionConfinedToConfig` — `noClasses().that().resideOutsideOfPackage("dev.jcputney.magika.config..").should().callConstructor(ContentTypeInfo.class).allowEmptyShould(true)`. Test scope excluded via existing `ImportOption.DoNotIncludeTests.class`. The rule was committed in T01 (Wave 0 RED state, fails until ContentTypeLabel is updated); turns green in T03 (`3cb17a4`).

**Doc-rot bug (deviation Rule 1, commit 3aaf7c0):** Once `ContentTypeLabelSentinelsTest` from T01 became exercisable post-T03, three of its four assertions failed — the v0.1 hardcoded descriptions disagreed with the bundled registry:

| Sentinel | v0.1 hardcoded | Bundled (oracle-pinned) | Status |
|----------|----------------|-------------------------|--------|
| TXT      | `Plain text`   | `Generic text document` | UPDATED |
| UNKNOWN  | `Unknown binary` | `Unknown binary data` | UPDATED |
| EMPTY    | `Empty file (size 0)` | `Empty file`     | UPDATED |
| UNDEFINED | (registry-miss only) | n/a              | unchanged |

Per CLAUDE.md ("parity tests fail loudly — no tolerance, no skipping") and plan T03 STOP-AND-ASK trigger: assertions were NOT softened. Sentinels updated to match upstream byte-for-byte. Javadoc on each constant now records the drift + the test that surfaced it. `LabelResolverTest` `isSameAs(ContentTypeLabel.TXT)` assertions still hold (object identity, not field values). 30 v0.1 parity fixtures still byte-for-byte clean (descriptions are not part of the parity comparison).

**Tests added/extended:**
- `ContentTypeLabelSentinelsTest` — 4 cases (TXT / UNKNOWN / EMPTY equal bundled registry rows; UNDEFINED returned for unknown label).
- `PackageBoundaryTest` — 7th ArchUnit rule.

### POST-02 / DEBT-03 — `ThresholdPolicy` not extracted (FIXED — commits 6dcf9ad + 57c6f1b)

**Root cause:** v0.1 `LabelResolver.effectiveThreshold` was an inlined `private static` switch. The v0.1 retrospective recorded the inlining as a deviation against the named POST-02 abstraction.

**ALG-14 gate (commit 6dcf9ad):** docs/algorithm-notes.md gained two new subsections in a single docs-only commit BEFORE any code change:
1. `### Bundled vs. upstream shape` (DEBT-02 IN-04 cross-ref) inside §"content_types_kb.json schema".
2. `### ThresholdPolicy class extraction (POST-02 / DEBT-03)` inside §"Prediction-mode threshold semantics".

Verified post-hoc:

```bash
DOCS_COMMIT=$(git log -S "ThresholdPolicy class extraction" --format='%H' -- docs/algorithm-notes.md | head -1)
# -> 6dcf9ad722023162ad0819907243132b29e011ae
CODE_COMMIT=$(git log --reverse --diff-filter=A --format='%H' -- src/main/java/dev/jcputney/magika/postprocess/ThresholdPolicy.java | head -1)
# -> 57c6f1b22978c967f84221a1cd7f7917539379af
git merge-base --is-ancestor "$DOCS_COMMIT" "$CODE_COMMIT"
# -> exit 0 (ALG-14 OK)
```

Plain `--diff-filter=A -- docs/algorithm-notes.md` would return the Phase 1 add commit (`0c28ca4`), which is why the content-anchored `-S` query is the right shape.

**Fix (commit 57c6f1b):** Created `dev.jcputney.magika.postprocess.ThresholdPolicy` — `public final` utility with single static method `resolve(PredictionMode, String, ThresholdConfig)`. Body byte-identical to the deleted `LabelResolver.effectiveThreshold` (same arms, same order, same `getOrDefault` fallback). `LabelResolver.java` line 66 now calls `ThresholdPolicy.resolve(...)`; the private method block is gone.

**Tests added:**
- `ThresholdPolicyTest` — 5 cases: BEST_GUESS returns 0.0 / MEDIUM_CONFIDENCE returns scalar / MEDIUM_CONFIDENCE ignores per-type map (regression for magika.py:610) / HIGH_CONFIDENCE uses per-type override / HIGH_CONFIDENCE falls back to scalar for unmapped label.

**`LabelResolverTest` invariant:** zero modifications across all 7 commits in this plan. The 8 existing cases pass UNMODIFIED — DEBT-03's stated success criterion.

### IN-01 — Redundant SHA-256 compute in `Magika` constructor (FIXED — commit efc93c4)

**Root cause:** `OnnxModelLoader.loadAndVerify()` already computes SHA-256 internally to verify against `EXPECTED_SHA256`, then `Magika.Magika(builder)` called `OnnxModelLoader.computeSha256(modelBytes)` again to populate `modelSha256` for the D-11 INFO log. ~30ms wasted per construction on the 3.0 MiB bundled model.

**Fix:** New `OnnxModelLoader.LoadedModel` record (bytes + verified sha256) returned by new `OnnxModelLoader.load()`. `Magika` constructor consumes `loaded.sha256()` directly. Existing `loadAndVerify()` and `computeSha256(byte[])` retained for back-compat (the existing 4 `OnnxModelLoaderTest` methods reference them; deletion would break compile).

**Tests added:** new `OnnxModelLoaderTest.load_returns_bytes_and_sha256_in_one_call` — asserts `LoadedModel.bytes().length > 100_000` and `LoadedModel.sha256()` equals `EXPECTED_SHA256`.

### IN-02 — Close-time `OrtException` silently swallowed (FIXED — commit efc93c4)

**Root cause:** `OnnxInferenceEngine.close()` had `catch (OrtException ignored)` with a comment "Session already dead — idempotent close contract." That hides genuine native-state corruption (use-after-close, double-close from another thread).

**Fix:** Added class-level `LOGGER` (SLF4J, keyed on `OnnxInferenceEngine.class`); replaced the swallow with `LOGGER.warn("OrtSession.close() threw; treating as already-closed", e)`. SLF4J `{}` parameterized logging with throwable param — no log injection (T-02-03 in threat model). Idempotent close contract preserved (`closed = true` flipped before `session.close()`). Per D-11, the WARN is a diagnostic, NOT a contracted INFO/ERROR site; `MagikaLoggingTest` (the D-11 three-event contract regression) still passes.

**Tests added:** `OnnxInferenceEngineCloseTest.close_after_normal_session_does_not_emit_warn` — `System.setErr` capture pattern from `MagikaLoggingTest`; asserts `buf.toString()` does NOT contain `"OrtSession.close() threw"`. The positive case (WARN on actual `OrtException`) is documented but not asserted (no MockAppender / logback dep, per CLAUDE.md no-unapproved-deps).

### IN-03 — `ExpectedResult.OverwriteReason.valueOf` without fixture context (FIXED — commit a7ec01e)

**Root cause:** `ExpectedResult.ExpectedPrediction.overwriteReasonEnum()` called `OverwriteReason.valueOf(overwriteReason)` directly. A sidecar with a typo (e.g. `"OVERWRITE_MAPP"`) raised a bare `IllegalArgumentException` from the JDK, with no fixture path in the message — the parity-test failure noise required manual fixture-by-fixture grep.

**Fix:** Added a fixture-aware overload `overwriteReasonEnum(java.nio.file.Path fixture)`. The 0-arg overload now delegates with `null` fixture (back-compat preserved). On `IllegalArgumentException`, the new overload throws `AssertionError` with message including `"Unrecognized overwriteReason '<bogus>' in sidecar for fixture: <path>"`.

**Tests added:** `ExpectedResultTest` — 2 cases (bogus value surfaces fixture path in `AssertionError`; null returns NONE).

### IN-04 — `algorithm-notes.md` missing `content_types_kb` shape cross-ref (FIXED — commit 6dcf9ad)

**Root cause:** Plan 1's vendoring rewrote the upstream `Map<String, Entry>` shape into a JSON-array `List<ContentTypeInfo>` (outer key promoted to a `label` field), but `algorithm-notes.md` §"content_types_kb.json schema" only described the upstream shape. Reading the doc would lead a future contributor to expect the wrong record declaration.

**Fix:** Added `### Bundled vs. upstream shape` subsection inside §"content_types_kb.json schema" (right after `### Field-type map`). Names the rewrite, cross-refs `docs/MODEL_CARD.md`, names `ContentTypeRegistry.fromList` as the consumer. Combined with DEBT-03's docs work into a single docs commit at the head of the plan.

### IN-05 — `FixtureLoader.discoverFixtures` silent orphan filtering (FIXED — commit a7ec01e)

**Root cause:** `discoverFixtures` filtered out any byte file lacking a sibling sidecar via `.filter(p -> Files.exists(...))`. Half-added fixtures silently dropped from the suite — a fixture-write workflow error had no observable signal.

**Fix:** Added `LOGGER` to `FixtureLoader` (SLF4J test-side, no D-11 contract). `discoverFixtures` body restructured: collect candidates, iterate, log SLF4J `WARN` per orphan with `fixtureRoot.relativize(...)` paths, skip but do not abort. Existing `UpstreamParityIT` count assertion still catches catastrophic loss (research recommendation: log-WARN over fail-fast).

**Tests added:** `FixtureLoaderOrphanTest` — 2 cases using `@TempDir` (orphan skipped + paired kept; README/ORACLE_VERSION excluded).

## ALG-14 Read-Before-Write Audit

```
$ git log --reverse --format='%h %s' d04205c..HEAD
a562741 test(02-01): scaffold Wave-0 RED tests for DEBT-01/02/03 + 7th ArchUnit rule
6dcf9ad docs(02-01): document ThresholdPolicy extraction + content_types_kb shape cross-ref     <-- ALG-14 GATE
3cb17a4 refactor(02-01): consolidate ContentTypeLabel sentinels via ContentTypeInfo (DEBT-01 / WR-04)
57c6f1b refactor(02-01): extract ThresholdPolicy from LabelResolver (DEBT-03 / POST-02)         <-- ThresholdPolicy.java added
efc93c4 refactor(02-01): close IN-01 (SHA-256 caching) + IN-02 (close-time WARN log) (DEBT-02)
a7ec01e refactor(02-01): close IN-03 (ExpectedResult fixture-context) + IN-05 (FixtureLoader WARN) (DEBT-02)
3aaf7c0 fix(02-01): align sentinel descriptions to bundled registry; spotless reformat (DEBT-01 / Rule 1)
```

Content-anchored ancestor check (the form the plan demands — plain `--diff-filter=A` would always return Phase 1's `0c28ca4` because algorithm-notes.md was added in Phase 1):

```bash
DOCS_COMMIT=$(git log -S "ThresholdPolicy class extraction" --format='%H' -- docs/algorithm-notes.md | head -1)
# 6dcf9ad722023162ad0819907243132b29e011ae
CODE_COMMIT=$(git log --reverse --diff-filter=A --format='%H' -- src/main/java/dev/jcputney/magika/postprocess/ThresholdPolicy.java | head -1)
# 57c6f1b22978c967f84221a1cd7f7917539379af
git merge-base --is-ancestor "$DOCS_COMMIT" "$CODE_COMMIT" && echo OK
# OK (exit 0)
```

T01's test-scaffold commit (`a562741`) precedes the docs commit because it's pure test infrastructure — it adds a `ThresholdPolicyTest.java` that *references* `ThresholdPolicy.resolve` (in RED state) but does NOT add `ThresholdPolicy.java` itself. The ALG-14 gate is specifically about the docs commit preceding the code commit that adds `ThresholdPolicy.java` to `src/main/java/`; the gate proof passes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Sentinel descriptions drifted from bundled registry (DEBT-01)**

- **Found during:** T07 verify gate (post-T06)
- **Issue:** Three hardcoded `ContentTypeInfo` descriptions (TXT/UNKNOWN/EMPTY) in v0.1 disagreed with the bundled `content_types_kb.json`. Surfaced as 3/4 failures in the new `ContentTypeLabelSentinelsTest`.
- **Fix:** Updated `ContentTypeInfo.{TXT, UNKNOWN, EMPTY}` constants to the upstream-true values (`"Generic text document"`, `"Unknown binary data"`, `"Empty file"`). Javadoc on each constant records the drift narrative. Per CLAUDE.md non-negotiable + plan T03 STOP-AND-ASK guidance, the assertions were NOT softened.
- **Files modified:** `src/main/java/dev/jcputney/magika/config/ContentTypeInfo.java`
- **Commit:** `3aaf7c0`

**2. [Rule 3 — Blocking] Spotless formatting violations (cosmetic but build-blocking)**

- **Found during:** T07 verify gate (Spotless `:check` step after Surefire+Failsafe both green)
- **Issue:** Two files needed reformatting per the project's Spotless config:
  - `src/main/java/dev/jcputney/magika/inference/onnx/OnnxModelLoader.java` — `LoadedModel` record body `{}` → `{\n  }`
  - `src/test/java/dev/jcputney/magika/parity/FixtureLoaderOrphanTest.java` — `(@TempDir Path tmp)` parameter wrapping
- **Fix:** Ran `mvn spotless:apply`. No semantic change; matches existing project style (`MagikaApiIT`'s `@TempDir` parameter style and the existing project record formatting).
- **Files modified:** the two files above
- **Commit:** `3aaf7c0` (combined with the Rule 1 fix — both emerged from the same T07 verify run)

### Authentication Gates Encountered

**T01 commit failed twice on first attempt with `1Password: agent returned an error / failed to fill whole buffer`.**

- **Type:** transient SSH-signing agent failure (not user-actionable on retry)
- **Resolution:** Per orchestrator's diagnostic update, the 1Password helper was responsive again; retried `git commit --no-verify` and the commit landed cleanly. Subsequent commits in this plan signed without issue. `--no-verify` (pre-commit hooks) was used per the parallel-execution protocol; signing was NOT bypassed.

## Verify Gate Result (T07)

`mvn -B -ntp verify` — **GREEN** at commit `3aaf7c0`:

| Suite      | Tests | Failures | Errors | Skipped |
|------------|-------|----------|--------|---------|
| Surefire   | 102   | 0        | 0      | 0       |
| Failsafe   | 60    | 0        | 0      | 0       |
| **Total**  | 162   | 0        | 0      | 0       |
| Spotless   | clean | —        | —      | —       |
| Licenses   | clean | —        | —      | —       |

**Surefire highlights:**
- `PackageBoundaryTest` 7/7 ArchUnit rules (was 6 in v0.1).
- `ThresholdPolicyTest` 5/5 (new).
- `ContentTypeLabelSentinelsTest` 4/4 (new).
- `OnnxInferenceEngineCloseTest` 1/1 (new — negative log-grep).
- `ExpectedResultTest` 2/2 (new).
- `FixtureLoaderOrphanTest` 2/2 (new).
- `OnnxModelLoaderTest` 5/5 (was 4 — +`load_returns_bytes_and_sha256_in_one_call`).
- `LabelResolverTest` 8/8 — UNMODIFIED across this entire plan.

**Failsafe highlights:**
- `UpstreamParityIT` 37/37 — zero parity disagreements; oracle pin unchanged at `magika @ 363a44183a6f300d5d7143d94a19e6a841671650`.
- `ConcurrencyIT` 3/3 — 8 threads × 100 iterations, no label smearing.
- `MagikaApiIT` 15/15 — D-11 three-event log contract preserved.
- `MagikaLoggingTest` 1/1 — D-11 INFO-on-load + INFO-on-close exactly once each. The new IN-02 WARN does NOT smear into the contract.
- `OnnxInferenceEngineIT` 4/4.

(Note: the plan body said "30 v0.1 fixtures"; `UpstreamParityIT` factory emits 37 dynamic tests because it includes the 30 oracle-pinned bytes plus the 5 edge fixtures added in Plan 01-07 + 2 named regression methods. All 37 green; no regression introduced by this plan.)

## Threat Flags

None. Phase 2 introduced no new attack surface — internal refactoring + test-side observability only. The four mitigated threats from `<threat_model>` (T-02-01 / T-02-02 / T-02-03 / T-02-04) are all locked by tests committed in this plan.

## Stub Tracking

None.

## Self-Check: PASSED

**Files claimed created in this SUMMARY — exist:**
- `src/main/java/dev/jcputney/magika/postprocess/ThresholdPolicy.java` ✓ (commit 57c6f1b)
- `src/test/java/dev/jcputney/magika/postprocess/ThresholdPolicyTest.java` ✓ (commit a562741)
- `src/test/java/dev/jcputney/magika/postprocess/ContentTypeLabelSentinelsTest.java` ✓ (commit a562741)
- `src/test/java/dev/jcputney/magika/inference/onnx/OnnxInferenceEngineCloseTest.java` ✓ (commit a562741)
- `src/test/java/dev/jcputney/magika/parity/ExpectedResultTest.java` ✓ (commit a562741)
- `src/test/java/dev/jcputney/magika/parity/FixtureLoaderOrphanTest.java` ✓ (commit a562741)

**Commits claimed in this SUMMARY — exist:**
- `a562741`, `6dcf9ad`, `3cb17a4`, `57c6f1b`, `efc93c4`, `a7ec01e`, `3aaf7c0` — all present in `git log d04205c..HEAD --oneline`.

**ALG-14 ordering — proven:**
- Docs commit `6dcf9ad` is ancestor of code commit `57c6f1b` per `git merge-base --is-ancestor`.

**LabelResolverTest invariant — proven:**
- `git log d04205c..HEAD --name-only -- src/test/java/dev/jcputney/magika/postprocess/LabelResolverTest.java` is empty (no commits modified it).

## Next Plan

`02-02-fixture-expansion` — TEST-11 (`randomtxt` overwrite-map oracle), TEST-12 (≥2 MEDIUM_CONFIDENCE fixtures), TEST-13 (≥2 BEST_GUESS fixtures), with `assertParity` extended to enforce strict `output.overwriteReason` equality. This plan's debt-closure is the prerequisite (per ROADMAP §Phase 2 intra-phase ordering rule).
