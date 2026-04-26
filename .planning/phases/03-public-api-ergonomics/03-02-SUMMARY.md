---
phase: 03-public-api-ergonomics
plan: "02"
subsystem: public-api
one_liner: "Lazy OrtSession init via synchronized-block ensureEngine() + identifyPaths batch overloads with per-call ForkJoinPool, A-02 Status mapping, and SC-6 concurrent-first-use contract (REF-02 + REF-04)"
requirements_completed: [REF-02, REF-04]
tags: [lazy-init, batch-identify, concurrency, forkjoinpool, synchronized, status-mapping]
dependency_graph:
  requires: [phase-03-plan-01, REF-01]
  provides: [REF-02, REF-04, identifyPaths-batch, lazy-OrtSession-init]
  affects: [Magika, MagikaLoggingTest, BuilderLazyInitTest, BatchIdentifyIT, ConcurrentLazyInitIT]
tech_stack:
  added: []
  patterns:
    - "synchronized-block lazy-init on private final Object lock (D-17 Pattern 1 — RESEARCH.md §Pattern 1)"
    - "volatile InferenceEngine field for fast-path reads (JMM publication guarantee)"
    - "per-call ForkJoinPool(parallelism) for batch — NOT parallelStream (D-12)"
    - "index-then-collect MagikaResult[] array for ordering guarantee (D-14)"
    - "A-02 exception-to-Status mapping: NoSuchFileException→FILE_NOT_FOUND_ERROR, AccessDeniedException→PERMISSION_ERROR, other IOException/InvalidInputException/InferenceException→UNKNOWN"
key_files:
  created:
    - src/test/java/dev/jcputney/magika/BuilderLazyInitTest.java
    - src/test/java/dev/jcputney/magika/BatchIdentifyIT.java
    - src/test/java/dev/jcputney/magika/ConcurrentLazyInitIT.java
  modified:
    - src/main/java/dev/jcputney/magika/Magika.java
    - src/test/java/dev/jcputney/magika/MagikaLoggingTest.java
decisions:
  - topic: "MagikaLoggingTest update for lazy-init (REF-04 amendment)"
    decision: "Added m.identifyBytes(new byte[]{'A'}) call inside info_log_on_load_and_close_each_fire_once before m.close(). Updated Javadoc to document the REF-04 shift: load INFO now fires from ensureEngine() on first identify*, not from constructor."
    rationale: "REF-04 moves D-11 load INFO from Magika() constructor to ensureEngine(). The existing MagikaLoggingTest called Magika.create() + close() with no identify* — under the lazy model, zero load events fire in that sequence. The test needed one identify* to trigger ensureEngine() so the count-of-one assertion remains meaningful. Per plan Task 1 Step 1 directive."
  - topic: "Multi-REQ pairing (D-02 preserved in implementation)"
    decision: "REF-02 (identifyPaths) and REF-04 (lazy OrtSession) are shipped in a single Magika.java change (commit 8c8382f) because SC-6 — exactly-one OrtSession + exactly-one D-11 INFO under N-thread first-use — is only testable once both exist."
    rationale: "Per 03-02 PLAN frontmatter decisions[] and 03-CONTEXT.md D-02. ConcurrentLazyInitIT uses identifyPaths to race first-use, which is exactly the natural shape that requires both REQs."
  - topic: "Spotless auto-format on new files (Rule 1 auto-fix)"
    decision: "Applied mvn spotless:apply after each new file creation. Google Java Format requires specific indentation rules (4-space alignment for continuation lines, 2-space for some block structures) that differ from the manually-written code."
    rationale: "Same Spotless behavior observed in plan 03-01. No code logic changed; purely formatting."
  - topic: "A-06 eager-SHA-lazy-OrtSession scope"
    decision: "Constructor retains: config load, registry load, OnnxModelLoader.load() (bytes+SHA), expectedTokens computation, outputContentTypes list. ensureEngine() has: new OnnxInferenceEngine(modelBytes, expectedTokens, ...) + D-11 load INFO."
    rationale: "A-06 from 03-CONTEXT.md post_research_amendments. getModelSha256() must return the correct SHA post-build pre-identify; the existing metadata_accessors_return_expected_values test passes without modification. build() <50ms SC-4 budget met (bytes+SHA ~10-15ms vs OrtSession ~hundreds of ms)."
metrics:
  duration_minutes: 6
  completed_date: "2026-04-26"
  tasks_completed: 5
  tasks_total: 5
  files_created: 3
  files_modified: 2
---

# Phase 03 Plan 02: Batch Identify and Lazy Init Summary

## What Was Built

REF-02 + REF-04 landed together (D-02 intentional pairing): lazy `OrtSession` initialization behind a `synchronized(engineLock)` block in `Magika`, plus `identifyPaths(List<Path>)` and `identifyPaths(List<Path>, int parallelism)` batch overloads. The D-11 three-event log contract is preserved — load INFO now fires from `ensureEngine()` on first identify* (not from the constructor), close INFO fires on `close()`, error ERROR fires only on actual error.

Key behavioral changes in `Magika.java`:
- `engine` field changed from `final InferenceEngine` to `volatile InferenceEngine engine = null`
- New `private final Object engineLock` lock object (anti-pattern: synchronizing on `this`)
- Constructor now eager-only: config, registry, bytes+SHA-256, expectedTokens, outputContentTypes
- `ensureEngine()` synchronized slow path: constructs `OnnxInferenceEngine`, fires D-11 load INFO, volatile-writes `engine`
- `identifyBytes/Path/Stream` each call `ensureEngine()` after `checkOpen()`
- `close()` null-checks `engine` before `local.close()` (SC-5: close-before-use must not load model)
- `engineForTest()` package-private accessor for SC-4/SC-5/SC-6 IT verification
- `identifyPaths` overloads: D-16 null pre-validation, D-12 per-call ForkJoinPool, A-02 Status mapping, D-14 index-then-collect ordering

Three new test files cover the new contracts:
- `BuilderLazyInitTest` (unit, 4 tests): SC-4 build <50ms, SC-5 close-before-use, A-06 eager-SHA, lifecycle engine reuse
- `BatchIdentifyIT` (parity IT, 8 tests): ordering, FILE_NOT_FOUND_ERROR, PERMISSION_ERROR, null-element NPE, null-list NPE, post-close ISE, parallelism=1, parallelism=0 IAE
- `ConcurrentLazyInitIT` (parity IT, 1 test): SC-6 N-thread first-use — identity equality + exactly-one D-11 load INFO

## Commits

| Hash | Type | Description |
|------|------|-------------|
| 8c8382f | feat | defer OrtSession.create() to first identify* via synchronized lazy-init (REF-04) + identifyPaths batch overloads (REF-02) + MagikaLoggingTest update |
| 582bb35 | test | add BuilderLazyInitTest for SC-4 + SC-5 (REF-04) |
| 5c683cb | test | add BatchIdentifyIT for SC-2 (REF-02) |
| 2f05fba | test | add ConcurrentLazyInitIT for SC-6 (REF-02 + REF-04 paired contract) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Spotless formatting on new/modified files**
- **Found during:** Task 1 (Magika.java + MagikaLoggingTest.java), Task 3 (BuilderLazyInitTest.java), Task 4 (BatchIdentifyIT.java), Task 5 (ConcurrentLazyInitIT.java)
- **Issue:** Google Java Format (Spotless) requires specific indentation for continuation lines and method chains that differ from manually-written code. The same pattern occurred in plan 03-01.
- **Fix:** Ran `mvn spotless:apply` after each new file creation before the commit; Spotless auto-corrected indentation.
- **Files modified:** All 5 Java files touched in this plan
- **Commits:** Spotless applied inline before each commit (no separate fix commit needed)

### MagikaLoggingTest Update (documented in decisions[])

The plan explicitly required investigating `MagikaLoggingTest` in Task 1 Step 1. The existing test called `Magika.create()` + `m.close()` with no `identify*` — under REF-04's lazy model, zero load INFO events fire in that sequence. Fixed by adding `m.identifyBytes(new byte[]{'A'})` before `close()`. Javadoc updated to explain the REF-04 amendment. This is expected behavior, not a bug.

## Known Stubs

None — all three test files exercise real `Magika` instances against real fixture files. `identifyPaths` maps real exceptions to real `Status` values. No hardcoded-empty data, no placeholder text, no mock data flows through the new API surface.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes at runtime trust boundaries. `identifyPaths` reads caller-supplied paths using the same `Files.*` APIs as `identifyPath`; the A-02 exception mapping is bounded and documented. `ensureEngine()` is package-private; `engineForTest()` is package-private. Per-call ForkJoinPool is shut down in `finally` (no thread leak).

## Self-Check: PASSED

- `src/main/java/dev/jcputney/magika/Magika.java` has `private volatile InferenceEngine engine = null` and `private final Object engineLock`
- `src/main/java/dev/jcputney/magika/Magika.java` has `ensureEngine()`, `engineForTest()`, `identifyPaths(List<Path>)`, `identifyPaths(List<Path>, int)`
- `src/main/java/dev/jcputney/magika/Magika.java` constructor calls `OnnxModelLoader.load()` (eager bytes+SHA) but NOT `new OnnxInferenceEngine(...)`
- `src/test/java/dev/jcputney/magika/MagikaLoggingTest.java` has `m.identifyBytes(new byte[] {'A'})` before `m.close()`
- `src/test/java/dev/jcputney/magika/BuilderLazyInitTest.java` exists with 4 tests — all pass
- `src/test/java/dev/jcputney/magika/BatchIdentifyIT.java` exists with 8 tests — all pass
- `src/test/java/dev/jcputney/magika/ConcurrentLazyInitIT.java` exists with 1 test — passes
- Commits 8c8382f, 582bb35, 5c683cb, 2f05fba all verified in git log
- `mvn -B -ntp verify` green: 180 tests (106 unit + 74 IT), 0 failures
