---
phase: 03-public-api-ergonomics
plan: "03"
subsystem: public-api
one_liner: "REF-03: JPMS module-info.java single-export dev.jcputney.magika + maven-invoker positive/negative consumer ITs."
requirements_completed: [REF-03]
tags: [jpms, module-info, maven-invoker, consumer-it, package-isolation, useModulePath]
dependency_graph:
  requires: [phase-03-plan-01, phase-03-plan-02, REF-01, REF-02, REF-04]
  provides: [REF-03, jpms-module-descriptor, positive-consumer-it, negative-consumer-it]
  affects: [pom.xml, src/main/java/module-info.java, src/it/]
tech_stack:
  added: [maven-invoker-plugin 3.9.1]
  patterns:
    - "useModulePath=false on Surefire AND Failsafe (Pitfall-1 fix — runs tests on classpath against the named module's classes, prevents ArchUnit IllegalAccessError)"
    - "maven-invoker-plugin src/it/ layout — positive consumer compile+run + negative consumer must-fail (invoker.buildResult=failure)"
    - "Multi-release JAR module-info.class extraction via javap to verify actual module name before writing requires"
key_files:
  created:
    - src/main/java/module-info.java
    - src/it/jpms-consumer-positive/pom.xml
    - src/it/jpms-consumer-positive/src/main/java/module-info.java
    - src/it/jpms-consumer-positive/src/main/java/consumer/Main.java
    - src/it/jpms-consumer-negative/pom.xml
    - src/it/jpms-consumer-negative/invoker.properties
    - src/it/jpms-consumer-negative/src/main/java/module-info.java
    - src/it/jpms-consumer-negative/src/main/java/consumer/Main.java
  modified:
    - pom.xml
decisions:
  - topic: "REF-03 amendment — no dev.jcputney.magika.api package (D-03)"
    decision: "Single export: exports dev.jcputney.magika;"
    rationale: "Existing structure already separates public (root + exception hierarchy) from internal (config / io / inference / postprocess / internal). Creating a .api package would force re-imports for every existing test fixture and future consumer with zero added isolation value. JPMS exports declarations are the single source of truth for public surface (D-04 — no new ArchUnit rule)."
  - topic: "ORT 1.25.0 Automatic-Module-Name verification (Pitfall 5)"
    decision: "Automatic-Module-Name: com.microsoft.onnxruntime"
    rationale: "Belt-and-suspenders re-verification at task time. Ran: unzip -p ~/.m2/repository/com/microsoft/onnxruntime/onnxruntime/1.25.0/onnxruntime-1.25.0.jar META-INF/MANIFEST.MF | grep Automatic-Module-Name. Literal output: 'Automatic-Module-Name: com.microsoft.onnxruntime'. Matches RESEARCH.md local-cache verification. The requires line in module-info.java uses this exact name."
  - topic: "Jackson 2.21.2 module name verification (Pitfall 6)"
    decision: "Jackson 2.21.2 ships a real module-info.class in a Multi-Release JAR (META-INF/versions/9/module-info.class). Module name is com.fasterxml.jackson.databind. No Automatic-Module-Name entry in MANIFEST.MF — the MR-JAR module-info takes precedence on Java 9+. Verified by: unzip -p ~/.m2/.../jackson-databind-2.21.2.jar META-INF/versions/9/module-info.class > module-info.class && javap module-info.class. Output: 'module com.fasterxml.jackson.databind@2.21.2 { ... }'. The requires line com.fasterxml.jackson.databind is correct."
    rationale: "RESEARCH.md warned Jackson 2.x module-info story historically inconsistent. Jackson 2.21.2 ships a proper multi-release module-info — no Automatic-Module-Name needed. Belt-and-suspenders re-verification at task time confirmed the exact module name before writing module-info.java."
  - topic: "JPMS consumer IT mechanism (D-22)"
    decision: "maven-invoker-plugin 3.9.1 with src/it/jpms-consumer-{positive,negative}/ projects"
    rationale: "Smallest diff vs alternatives (multi-module conversion, shaded test-jar). Standard Maven idiom for 'test the artifact like a real consumer'. Build-plugin scope only — not a runtime/test classpath dependency, so no CLAUDE.md 'no unapproved deps' violation."
  - topic: "license-maven-plugin and module-info.java"
    decision: "No exclude needed. license-maven-plugin accepts module-info.java with the standard Apache 2.0 block comment header."
    rationale: "The license plugin's includes pattern (src/main/java/**/*.java) matched module-info.java. The plugin checked the file and found the Apache 2.0 header in the block comment — mvn verify passed without any exclude configuration. The file does not need a package statement for the header check to succeed."
  - topic: "Spotless formatting on module-info.java"
    decision: "Spotless added a blank line between the exports and requires sections. Applied via mvn spotless:apply (same pattern as plans 03-01 and 03-02)."
    rationale: "Google Java Format enforces specific spacing that differs from manually-written code. Spotless check caught a missing blank line between exports and requires. Applied spotless:apply inline before commit — no separate fix commit needed."
metrics:
  duration_minutes: 4
  completed_date: "2026-04-26"
  tasks_completed: 4
  tasks_total: 4
  files_created: 8
  files_modified: 1
---

# Phase 03 Plan 03: JPMS Module-Info Summary

## What Was Built

REF-03 landed: `src/main/java/module-info.java` declares `module dev.jcputney.magika` with a single export (`exports dev.jcputney.magika`) and three `requires` directives — `com.fasterxml.jackson.databind` (real MR-JAR module), `com.microsoft.onnxruntime` (Automatic-Module-Name), and `org.slf4j` (real module-info in SLF4J 2.0+). No `requires transitive` — Jackson and ORT are implementation details (D-21).

The Pitfall-1 fix (`<useModulePath>false</useModulePath>` on both Surefire AND Failsafe) was applied in Task 1 before module-info.java landed, ensuring the existing 180-test suite kept running on the classpath rather than the module path. This prevents ArchUnit's `@AnalyzeClasses` from hitting `IllegalAccessError` on the unnamed test module.

Two maven-invoker-plugin 3.9.1 consumer IT projects verify both halves of the REF-03 contract:
- `jpms-consumer-positive`: declares `requires dev.jcputney.magika;`, exercises `Magika.create()` + `identifyBytes` + `Status.OK` check — compiles and runs successfully
- `jpms-consumer-negative`: imports `dev.jcputney.magika.inference.InferenceEngine` (non-exported internal type) — FAILS compilation as expected; `invoker.buildResult=failure` records this as a passing test

ORT 1.25.0 module name was verified at task time (`Automatic-Module-Name: com.microsoft.onnxruntime`). Jackson 2.21.2's module name was verified by extracting `META-INF/versions/9/module-info.class` from the MR-JAR and running `javap` (`module com.fasterxml.jackson.databind@2.21.2`).

## Commits

| Hash | Type | Description |
|------|------|-------------|
| 6891cef | build | add useModulePath=false to Surefire + Failsafe (Pitfall-1 fix) |
| df92403 | feat | add module-info.java with single export dev.jcputney.magika (REF-03) |
| 61b8049 | test | wire maven-invoker-plugin + positive JPMS consumer IT |
| 6b89a6e | test | add negative JPMS consumer IT — must-fail compile on internal-class reference |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Spotless formatting on module-info.java**
- **Found during:** Task 2
- **Issue:** Google Java Format requires a blank line between the `exports` and `requires` sections in module-info.java; manually written file did not have it.
- **Fix:** Ran `mvn spotless:apply` inline before commit. Same pattern observed in plans 03-01 and 03-02.
- **Files modified:** `src/main/java/module-info.java`
- **Commit:** Spotless applied inline before df92403 (no separate fix commit)

### Jackson 2.21.2 Module-Name Discovery (not a deviation — new information)

RESEARCH.md recorded that Jackson 2.x's module-info story was "historically inconsistent" and flagged it as Pitfall 6. At task time, discovered Jackson 2.21.2 ships a proper Multi-Release JAR with `META-INF/versions/9/module-info.class`. No `Automatic-Module-Name` entry exists in `MANIFEST.MF`; the MR-JAR module-info takes precedence. The module name `com.fasterxml.jackson.databind` matches what RESEARCH.md expected — no plan change needed.

## Known Stubs

None — `module-info.java` is a complete JPMS descriptor with no placeholder values. The positive consumer IT exercises a real `Magika` instance end-to-end. The negative consumer IT references a real internal type. All contracts are fully wired.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: information_disclosure | src/it/jpms-consumer-negative/src/main/java/consumer/Main.java | References internal package `dev.jcputney.magika.inference.InferenceEngine` by FQN — accepted per T-03-12 (package name is already in CLAUDE.md architectural design, no secret info). Severity: NONE. |

## Self-Check: PASSED

- `src/main/java/module-info.java` exists with `module dev.jcputney.magika { exports dev.jcputney.magika; ... }`
- `grep -c '<useModulePath>false</useModulePath>' pom.xml` returns 2
- `grep -c 'exports ' src/main/java/module-info.java` returns 1
- No `requires transitive` directive in module-info.java (only in Javadoc comment)
- No internal subpackage export (`config|io|inference|postprocess|model|internal`) in module-info.java
- `src/it/jpms-consumer-positive/pom.xml` contains `@project.version@`
- `src/it/jpms-consumer-negative/invoker.properties` contains `invoker.buildResult=failure`
- `src/it/jpms-consumer-negative/src/main/java/consumer/Main.java` imports `dev.jcputney.magika.inference.InferenceEngine`
- Commits 6891cef, df92403, 61b8049, 6b89a6e verified in git log
- `mvn -B -ntp verify` green: 180 tests (106 unit + 74 IT), invoker Passed: 2, Failed: 0
- All 7 PackageBoundaryTest ArchUnit rules pass (7/7 confirmed by `mvn -B -ntp test -Dtest=PackageBoundaryTest`)
- maven-invoker summary: `jpms-consumer-positive ... SUCCESS`, `jpms-consumer-negative ... SUCCESS` (expected failure recorded as passing)
