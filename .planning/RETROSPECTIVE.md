# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v0.1 — Skeleton + Parity v0.1

**Shipped:** 2026-04-24
**Phases:** 1 | **Plans:** 6 + 1 gap-closure pass | **Sessions:** ~13 hours single-day sprint

### What Was Built

- **Public API**: `Magika` (final, AutoCloseable) + `MagikaBuilder` + `MagikaResult` / `MagikaPrediction` records, with three entry points (`identifyPath` / `identifyBytes` / `identifyStream`) wired through one internal pipeline. Thread-safe identify*; idempotent close; post-close `IllegalStateException("Magika has been closed")`.
- **ONNX Runtime integration**: `OnnxInferenceEngine` + `OnnxModelLoader` under `inference/onnx/` (the only package allowed to touch `ai.onnxruntime.*`, enforced at bytecode level by ArchUnit). FP-deterministic session (BASIC_OPT, intraOpNumThreads=1, INT32 input, IntBuffer tensor). Bundled `standard_v3_3` model (3.0 MiB, SHA-256 verified at load time, 4-axis chain match across file/loader/MODEL_CARD/algorithm-notes).
- **Postprocess pipeline**: `LabelResolver` (verbatim 5-step Python pseudocode, overwrite-map BEFORE threshold), `FallbackLogic` (small-file/empty branches with `ContentTypeLabel.UNDEFINED` sentinel), `Utf8Validator` (strict `CharsetDecoder.REPORT`).
- **Algorithm contract**: `docs/algorithm-notes.md` (861 lines, 14 H2 headings) — first commit before any gated `.java`, citing `magika.py:578-634` overwrite, `:712-792` small-file, `:794-847` tensor, `:60` prediction-mode-default. ALG-14 hard gate honored via git history.
- **Parity harness**: `UpstreamParityIT` `@TestFactory` emitting one `DynamicTest` per fixture (30 fixtures across 8 categories) + 7 named regression tests; `ConcurrencyIT` (8 threads × 2 inputs × 100 iterations = 800 identifications, zero label smearing). Strict label equality + 1e-4 score tolerance; oracle pinned to `magika==1.0.2` @ git SHA `363a44183a6f300d5d7143d94a19e6a841671650`.
- **Bytecode boundaries**: `PackageBoundaryTest` with 6 ArchUnit rules (ORT confined to `inference.onnx`, Jackson confined to `config`, no Jakarta/CDI/Spring) — catches both imports and FQN references.
- **Build infrastructure**: Maven skeleton with JDK 17 release, `-parameters` (Jackson record requirement), reproducible `outputTimestamp`, Spotless (Eclipse JDT), Mycila license-maven-plugin 5.0.0, Surefire/Failsafe split via JUnit 5 tag taxonomy (`unit` / `parity`), source + javadoc jar attachment.
- **CI**: 3-OS GitHub Actions matrix (`ubuntu-latest` / `macos-latest` / `windows-latest`, fail-fast: false, Java 17 Temurin).
- **Namespace claim**: `dev.jcputney` verified on Sonatype Central Portal via DNS TXT on `jcputney.dev` (publish itself deferred to Phase 3).

### What Worked

- **Read-before-write enforced by git history (ALG-14)**. Authoring the 861-line algorithm-notes doc as the literal first content commit — *before* any `inference/` / `postprocess/` / `io/` / `config/` `.java` could land — meant every subsequent plan had a verbatim Python citation table to reach for, not a guess. Verifier proves ordering by `git log --reverse`. This is a pattern worth copying into future milestones whenever upstream-parity is the product.
- **3-source requirement cross-reference (REQUIREMENTS.md / VERIFICATION.md / SUMMARY frontmatter)**. The audit caught doc-rot in REQUIREMENTS.md's checkbox list (~18 stale `[ ]`) by triangulating against two independent sources. A single-source check would have flagged false gaps.
- **Code review → gap-closure pass (01-07)**. Surfacing CR-01 (stream skipped small-file branch), CR-02 (post-token short-content branch), and 3 warning-level latent defects (WR-01/02/03) *after* the initial verification but *before* milestone close meant 5 atomic fix commits + 4 oracle-pinned regression fixtures landed cleanly. The failure mode of "verifier said pass, reviewer found issues, audit caught everything" is exactly what a multi-pass workflow is for.
- **Bytecode-level package isolation via ArchUnit**. Spotless/Checkstyle import bans miss FQN references; ArchUnit's `noClasses().dependOnClassesThat().resideInAPackage(...)` form catches both. The `onlyBeAccessed` form + `dependOnClassesThat` form together cover the symmetric directions and the rule has *teeth* once ORT classes are imported (Plan 4).
- **Single-day execution**. 7 plans + 23 commits + 144 tests in ~13 hours. The non-negotiable upfront constraints (parity is the product, no `@Disabled`, no softening) eliminated entire categories of mid-flight ambiguity.
- **Oracle pin as machine-readable file (`fixtures/ORACLE_VERSION`)**. Future model refreshes regenerate against a documented commit SHA, not "whatever upstream is now." Already proved its value in 01-07 — the 4 new fixtures regenerated against the same pin without drift.

### What Was Inefficient

- **REQUIREMENTS.md checkbox drift**. The v1 checklist at the top of REQUIREMENTS.md and the traceability table at the bottom diverged silently — the table reflected actual completion while ~18 checkboxes stayed `[ ]`. Caught at audit time. **Mitigation for v0.2:** wire a doc-consistency lint (or just stop maintaining the dual source) — pick one as authoritative.
- **SDK accomplishments extraction grabbed REVIEW headers**. `gsd-sdk milestone.complete` pulled "1. [Rule 1 - Bug]" and bare "One-liner:" / "Root cause:" lines from SUMMARY frontmatter into MILESTONES.md. Hand-overwrite was needed. **Possible improvement:** the SUMMARY frontmatter should expose a dedicated `one_liner:` field per plan rather than relying on heuristic extraction.
- **POST-02 design deviation surfaced late**. The implementation inlined `ThresholdPolicy` into `LabelResolver.effectiveThreshold()` (~5 lines). Behavior is fully exercised, but the requirement-as-written specified a separate class. Caught at validation/Nyquist time, documented as a deviation. **Mitigation:** when a plan author chooses to inline an abstraction the requirement names, surface that as a `decisions:` entry in the plan SUMMARY so audit doesn't need to discover it.
- **One-day scope risk**. Compressing 7 plans into 13 hours meant most plans had no "soak" between author/verify cycles. Worked here because parity test failures are loud — would not work for UI-shaped features where ambiguity hides.

### Patterns Established

- **`docs/algorithm-notes.md` as canonical reference cited from code comments + plan frontmatter + verification truth tables.** Every claim about upstream behavior anchors to a Python line citation; if the citation breaks, the claim breaks. Apply this pattern any time the product is "match an external behavior."
- **Sidecar `.expected.json` per fixture + machine-readable oracle pin.** Decouples "what does the fixture look like?" from "what should the answer be?" Reproducible regeneration via documented recipe (no helper script per project rule "no helper scripts").
- **Hard gates evidenced by command output, not assertion.** ALG-14 ordering proved by `git log --reverse | head` showing docs commit precedes first `.java`. SHA chain proved by 4 parallel grep commands. Build green proved by raw mvn output. Easier to re-verify than re-prove.
- **`@Tag("parity")` taxonomy on integration tests + Failsafe gating.** Surefire (unit) green during fast loops; Failsafe (parity) green on `verify`. Lets developers iterate without the 3.0 MiB model load on every save.
- **6 ArchUnit rules (`PackageBoundaryTest`) with `@ArchTag('unit')` rather than `@Tag`.** ArchUnit's custom JUnit 5 engine doesn't honor Jupiter `@Tag` for descriptor filtering — discovered the hard way when the test silently ran zero rules under `<groups>unit</groups>`. Now baked into the pattern.
- **Single Magika instance + `volatile closed` flag + post-close `IllegalStateException("Magika has been closed")` (verbatim message asserted).** Idempotent close, undefined behavior under concurrent close+identify documented in Javadoc with "JVM may crash with native access violation" wording.
- **PROC-02 SUMMARY frontmatter standard.** Every `.planning/phases/*/*SUMMARY.md` carries three standardized fields enforced at milestone-close by `DocConsistencyLintTest` (PROC-01): `one_liner` (string ≤120 chars; the SUMMARY's elevator pitch — heading-tolerant by design so the gsd-sdk `milestone.complete` accomplishments extractor can read it directly OR scrape headers around it without losing meaning, per D-14), `requirements_completed` (YAML list of REQ-IDs matching `^[A-Z]+-[0-9]+$`; closes the v0.1 audit drift between top-of-REQUIREMENTS.md checkboxes and bottom-of-REQUIREMENTS.md traceability table), and `decisions` (OPTIONAL — when present, a list of `{topic, decision, rationale}` mapping objects per PROC-03; absence means "no deviations recorded" and is NOT a violation per B-11). The lint also accepts arbitrary additional non-standardized fields (`subsystem`, `tags`, `dependency_graph`, `tech_stack`, `findings_closed`, `key_files`, `commits`, `metrics`) via Jackson `@JsonIgnoreProperties(ignoreUnknown=true)` — the standard locks the three required shapes without forbidding richness around them. **B-06 mechanism note:** PROC-01's "informational log line on `.planning/` absence" is mechanically realized as a JUnit `Assumptions.assumeTrue(Files.isDirectory(Path.of(".planning")), "...")` producing a Surefire test-report `<skipped message="...">` entry — NOT an SLF4J INFO log line. The test-report skip is the more-precise audit artifact (visible in `target/surefire-reports/TEST-*.xml` AND the `Tests run: N, Failures: 0, Errors: 0, Skipped: 1` console summary line); apply this when reading PROC-01's wording. Canonical underscore-variant target shape: see `03-01-SUMMARY.md` lines 1-59.

### Key Lessons

1. **Parity-as-product means parity-as-gate.** When the only thing that makes the library worth shipping is byte-for-byte agreement with an upstream, every test is a parity test. There's no "softer" mode that's still useful — make this explicit in CLAUDE.md (already done) and refuse `@Disabled` as a closure mechanism.
2. **Process gates are durable when encoded in git history, not just docs.** ALG-14 says "doc before code" — but the *enforcement* is `git log --reverse` showing the doc commit lands first. Future audits replay the proof in seconds. If the constraint can be expressed as "commit X must precede commit Y in chronological order," express it that way.
3. **Audit needs three sources, not two.** REQUIREMENTS.md vs VERIFICATION.md alone would have missed the SUMMARY-frontmatter cross-check that caught `requirements-completed` field-name drift across plans (some used `requirements-completed`, others `requirements_closed`, others `requirements_addressed`). Three sources lets you spot which one is wrong.
4. **Multi-pass verification (verifier → reviewer → audit) catches different defect classes.** Verifier checks plan truths against code. Reviewer reads code with fresh eyes. Audit checks the milestone against its definition of done. Each catches what the previous misses. Keep all three for any milestone where the product is correctness.
5. **`.planning/` gitignored is fine if the git tag is the durable marker.** All milestone artifacts live local-only by user preference. The `v0.1` annotated tag captures the shipping summary in a permanent, push-able form. Don't fight the gitignore rule; just use what git already gives you.

### Cost Observations

- Model mix: predominantly Opus (single-day milestone with verifier/reviewer/audit passes) — exact split not measured. Subagents (gsd-integration-checker, gsd-verifier, code review) used Sonnet per `balanced` profile.
- Sessions: appears to be one extended session ending in the milestone close.
- Notable: the inline `01-07` gap-closure pass — finding 5 issues post-verification, fixing each as an atomic commit, and re-verifying — converted what could have been a "ship with debt" decision into a clean closure. Worth budgeting time for this style of pass at every milestone, not optimizing it away.

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v0.1 | 1 (long) | 1 | First milestone — established ALG-14 pattern (doc-before-code via git history), 3-source audit cross-reference, multi-pass verification (verifier → reviewer → audit), oracle-pinned parity fixtures |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v0.1 | 144 (85 unit + 59 IT/parity) | 85/85 v1 requirements + 9/9 E2E flows + 6 ArchUnit rules + Nyquist-compliant | 0 unapproved deps (ORT 1.25.0, Jackson 2.21.2, SLF4J 2.0.17, JUnit 6.0.3, AssertJ 3.27.7, ArchUnit — all pre-approved per STACK.md) |

### Top Lessons (Verified Across Milestones)

1. *(awaiting v0.2 to verify across milestones)*

---

*Last updated: 2026-04-24 after v0.1 milestone close*
