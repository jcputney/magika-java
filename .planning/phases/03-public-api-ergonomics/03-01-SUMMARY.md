---
phase: 03-public-api-ergonomics
plan: "01"
subsystem: public-api
one_liner: "REF-01: 4-value Status enum + 4-component MagikaResult record; @JsonProperty wire mapping; 35 sidecars regen'd."
requirements_completed: [REF-01]
tags: [status-enum, magika-result, parity, sidecar-regen, archunit]
dependency_graph:
  requires: [phase-02]
  provides: [REF-01, Status-enum, 4-component-MagikaResult]
  affects: [MagikaResult, Magika, UpstreamParityIT, ExpectedResult, PackageBoundaryTest, all-35-sidecars]
tech_stack:
  added: []
  patterns:
    - "@JsonProperty on enum constants (first in this codebase — Status.java)"
    - "ArchUnit doNotHaveFullyQualifiedName carve-out for CFG-04 rule"
    - "Python json.dumps with sort_keys=True for deterministic sidecar regen"
key_files:
  created:
    - src/main/java/dev/jcputney/magika/Status.java
  modified:
    - src/main/java/dev/jcputney/magika/MagikaResult.java
    - src/main/java/dev/jcputney/magika/Magika.java
    - src/test/java/dev/jcputney/magika/PackageBoundaryTest.java
    - src/test/java/dev/jcputney/magika/parity/ExpectedResult.java
    - src/test/java/dev/jcputney/magika/parity/UpstreamParityIT.java
    - src/test/resources/fixtures/README.md
    - README.md
    - "src/test/resources/fixtures/**/*.expected.json (35 files)"
decisions:
  - topic: "Wire-format spike result"
    decision: |
      Literal upstream JSON for src/test/resources/fixtures/images/sample.jpg:
      {
        "dl": {"label": "jpeg", "overwriteReason": "NONE", "score": 0.9998916387557983},
        "output": {"label": "jpeg", "overwriteReason": "NONE", "score": 0.9998916387557983},
        "score": 0.9998916387557983,
        "status": "ok",
        "upstream_magika_git_sha": "363a44183a6f300d5d7143d94a19e6a841671650",
        "upstream_magika_version": "1.0.2"
      }
    rationale: "Per D-07 / A-04: confirms upstream serialization of Status field. status.value='ok' is the wire string. All 35 fixture paths exist and are readable so all sidecars land status='ok' per A-04. Spike ran against pinned oracle magika@363a44183a6f300d5d7143d94a19e6a841671650 via uv tool run."
  - topic: "ArchUnit/Jackson confinement vs @JsonProperty on Status"
    decision: "Option α — relax CFG-04 to allowlist dev.jcputney.magika.Status"
    rationale: "Smallest test-code diff (~3 lines). Status is the only public-API enum that needs upstream-aligned wire-format mapping; future enums in the public API package will be reviewed individually. CFG-04's architectural intent — keep Jackson code paths in config — is preserved for code-bearing classes; annotation-only references on a public enum are a documented carve-out. Implemented via .and().doNotHaveFullyQualifiedName('dev.jcputney.magika.Status') on the existing noClasses().that() predicate (ArchUnit 0.10+ stable public API; verified against ArchUnit 1.3.0 in use). PackageBoundaryTest 7 rules all pass."
  - topic: "Sidecar regen batch ordering"
    decision: "3 batches: (1) archives/code/documents/images=12, (2) edge=17, (3) structured/text=6"
    rationale: "Mirrors Phase 2 precedent (02-02-fixture-expansion-PLAN.md). All 35 sidecars regenerated as a single filesystem operation (Python json.dumps with sort_keys=True, 2-space indent); batched commits stage subsets for git history readability. Actual edge count was 17 not 18 (plan had miscounted), and text count was 4 not 3, making batch 3 = 6. Total 12+17+6=35 correct."
  - topic: "Spotless formatting: @JsonProperty annotation placement"
    decision: "Annotation and enum constant on same line: @JsonProperty(\"ok\") OK,"
    rationale: "Rule 1 auto-fix. Spotless (Google Java Format) requires @JsonProperty annotation to be inline with the enum constant, not on a separate line. Applied via mvn spotless:apply on Status.java before final verify run."
metrics:
  duration_minutes: 30
  completed_date: "2026-04-26"
  tasks_completed: 4
  tasks_total: 4
  files_created: 1
  files_modified: 43
---

# Phase 03 Plan 01: Status Enum and Record Summary

## What Was Built

REF-01 landed: a public 4-value `Status` enum verbatim-mirrored from upstream Python (`OK | FILE_NOT_FOUND_ERROR | PERMISSION_ERROR | UNKNOWN`), with `@JsonProperty` wire-format annotations on each constant. `MagikaResult` extended to a 4-component record carrying `Status status` with non-null validation. All 35 oracle-pinned sidecar JSONs regenerated with the new `status: "ok"` field. `UpstreamParityIT.assertParity` tightened to a 6th strict-equality boolean (`statusOk`). Source-break documented in `README.md`. ROADMAP SC-1 wording amended to the verbatim 4-value list. `mvn -B -ntp verify` green: 167 tests (102 unit + 65 IT), 0 failures, 0 parity disagreements.

## Commits

| Hash | Type | Description |
|------|------|-------------|
| 4574376 | feat | introduce Status enum + extend MagikaResult to 4-component record (REF-01) |
| add6fe8 | test | extend ExpectedResult + assertParity for strict Status equality + update fixtures README |
| af27a12 | test | regen sidecars (archives/code/documents/images) with status field |
| b81607f | test | regen edge sidecars with status field |
| 81bd304 | test | regen sidecars (structured/text) with status field |
| 0a12cc0 | docs | document v0.2 source-break + amend ROADMAP SC-1 wording |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Spotless formatting: @JsonProperty annotation placement**
- **Found during:** Task 3 (full mvn verify run)
- **Issue:** Status.java had each `@JsonProperty` annotation on its own line above the enum constant. Spotless (Google Java Format) requires the annotation to be inline: `@JsonProperty("ok") OK,`
- **Fix:** Ran `mvn spotless:apply` to get the exact format, then included the corrected `Status.java` in the Task 3 Java test changes commit.
- **Files modified:** `src/main/java/dev/jcputney/magika/Status.java`
- **Commit:** add6fe8

### Minor Count Deviation (Non-Bug)

The plan stated batch 2 (edge) = 18 files and batch 3 (structured/text) = 5 files. Actual counts: edge = 17, structured/text = 6. Total = 35 correct. The plan had miscounted `text/` as 3 files when there are 4 (`sample.csv`, `sample.json`, `sample.md`, `sample.yaml`). No code impact.

## Known Stubs

None — all 35 sidecars carry `"status": "ok"` and all parity assertions pass with strict Status equality. No placeholder or hardcoded-empty data flows to UI or test assertions.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes at runtime trust boundaries introduced. The sidecar regen is author-time only (pinned oracle SHA `363a44...`); the `Status` enum is a bounded 4-value set with documented `@JsonProperty` wire mapping.

## Self-Check: PASSED

- `src/main/java/dev/jcputney/magika/Status.java` exists with 4 constants and 4 @JsonProperty annotations
- `src/main/java/dev/jcputney/magika/MagikaResult.java` has 4-component record with requireNonNull(status)
- `src/main/java/dev/jcputney/magika/Magika.java` has `return new MagikaResult(dl, out, r.score(), Status.OK);`
- `src/test/java/dev/jcputney/magika/PackageBoundaryTest.java` has `doNotHaveFullyQualifiedName("dev.jcputney.magika.Status")`
- All 35 `*.expected.json` sidecars carry `"status": "ok"`
- `README.md` has `## Breaking changes in v0.2`
- `.planning/ROADMAP.md` SC-1 reads `FILE_NOT_FOUND_ERROR | PERMISSION_ERROR | UNKNOWN`
- Commits 4574376, add6fe8, af27a12, b81607f, 81bd304, 0a12cc0 all verified in git log
- `mvn -B -ntp verify` green: 167 tests, 0 failures
