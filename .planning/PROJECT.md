# magika-java

## What This Is

A clean-room Java library that wraps Google Magika's ONNX model for AI-driven
file-type detection. Not a fork of `google/magika`, not a fork of the stale
`ardoco/magika` artifact — fresh codebase with our own Maven coordinates. Target
audience: Java/JVM developers who want Magika's detection accuracy from a
dependency-light, POJO-friendly library.

## Core Value

Identical detection output to upstream Magika for the same input, from pure
Java. If parity breaks, the library is worthless — everything else is secondary.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

<!-- Current scope. Building toward Phase 1 (single-module v0.1 skeleton). -->

- [ ] Load bundled `standard_v3_3` ONNX model via ONNX Runtime Java
- [ ] Public API: `identifyBytes(byte[])`, `identifyPath(Path)`, `identifyStream(InputStream)`
- [ ] `MagikaResult` shape mirrors upstream Python API (`dl`, `output`, `score`)
- [ ] Parity test suite against a curated subset of upstream `tests_data/basic/` fixtures
- [ ] Algorithm notes doc capturing Python↔Rust divergences, written before any implementation code
- [ ] README with attribution (Google, upstream repo, Apache 2.0, "independent community binding — not official")
- [ ] Google issue draft (`docs/google-issue-draft.md`) asking about model-versioning contract, community-binding linkage, and groupId conventions
- [ ] Builder + AutoCloseable `Magika` lifecycle, thread-safe for `identify*()` calls

### Out of Scope

<!-- Explicit boundaries. Phase 1 only. Doors stay open for later milestones. -->

- **CLI module** — not building a command-line wrapper in v1. Upstream ships their own CLI; Java users will embed the library.
- **Polyglot detection** — not in v1. Upstream's polyglot mode is additional complexity we don't need for first release.
- **Custom model loading** — v1 bundles exactly one model (`standard_v3_3`). API surface stays minimal; override hooks added only when a second model ships.
- **Fine-tuning hooks** — out of scope. Magika's model is pretrained; we consume it.
- **Jakarta EE integration module** — not in v1, but project layout must tolerate becoming multi-module (mirrors `mjml-java`'s structure).
- **Spring / CDI integration** — same reasoning; keep the core POJO.
- **GPU ONNX Runtime variant** — CPU inference is fast enough for file-type detection; GPU adds a second native-lib matrix we don't need.
- **Platform-specific classifier JARs** — shipping one fat `onnxruntime` dep is simpler for v1; revisit if artifact size becomes a problem.
- **Maven Central publication in Phase 1** — infra (Sonatype OSSRH, GPG signing, release automation) is deferred to a later milestone. Phase 1 proves the library works locally.

## Context

**Upstream references:**
- Main repo: https://github.com/google/magika (Apache 2.0)
- Current model: `assets/models/standard_v3_3/` (ONNX + JSON thresholds)
- Python source: `python/` — the readable reference implementation; **read first**
- Rust source: `rust/` — the current canonical implementation
- Prior stale Java port (reference only, **do not fork or copy**): https://github.com/ArDoCo/magika (pinned to v0.6.0)

**ONNX Runtime Java ecosystem:**
- `com.microsoft.onnxruntime:onnxruntime` is a single Maven artifact that bundles native libs for Linux / macOS / Windows on x86_64 and aarch64 (~20 MB total compressed)
- `OrtSession.run()` is thread-safe for concurrent inference
- Session creation is expensive (hundreds of ms + memory for model weights), which favors a single long-lived `Magika` instance over per-call patterns

**Known algorithmic quirks to investigate (from upstream issue tracker skimming):**
- "Overrule" logic where `output.label != dl.label` when thresholds fall back to a generic label (`txt`, `unknown`, etc.)
- Text-subtype refinement via charset/content heuristics for the `txt` fallback — may require porting regardless of model output
- Small-file handling: files smaller than one window size have special padding behavior

**Similar-pattern Java library:** `mjml-java` — POJO core with room to grow into a multi-module layout. Use its structure as a reference shape.

## Constraints

- **Tech stack**: JDK 17 baseline — widest support for active LTSs, aligns with ONNX Runtime Java's own minimum
- **Tech stack**: Maven, single module in Phase 1 — multi-module structure is a later concern
- **Tech stack**: `com.microsoft.onnxruntime:onnxruntime` (latest stable at Phase 1 plan time)
- **Tech stack**: Jackson (latest 2.x stable) for threshold config JSON parsing
- **Tech stack**: JUnit 5 + AssertJ for tests
- **Tech stack**: SLF4J API in core (no binding; consumer's choice) — deferred decision; can be dropped to zero-logging if it feels like dead weight
- **API surface**: Core library MUST be POJO — no Jakarta EE, no Spring, no CDI in the core module
- **Process**: Read Python + Rust implementations and document divergences **before** writing Java implementation code. This is a hard gate, encoded as Phase 1 Plan 1.
- **Process**: No unapproved dependencies. New dep = stop and ask.
- **Process**: Parity tests fail loudly; no tolerance, no skipping, no softening. Every disagreement with upstream is an investigation.
- **Process**: Stop and ask before any non-obvious architectural decision (explicit user preference).
- **Licensing**: Apache 2.0. Preserve upstream `LICENSE` / `NOTICE` in the resources tree with clear provenance.
- **Dependency rule**: Do not add CLI module, helper scripts, or other deps without explicit approval.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Native-library distribution: single fat `onnxruntime` Maven artifact | ONNX Runtime Java ships one JAR that contains all platform natives (~20 MB); simpler than classifiers for v1. Revisit if artifact size or GPU support becomes relevant. | — Pending |
| Threshold config loaded as static immutable Java record | We ship exactly one model per release; per-instance overrides aren't needed until Phase 2 custom-model work. Parse `thresholds.json` once at class load via Jackson. | — Pending |
| Public API: builder + AutoCloseable `Magika` instance | `OrtSession` creation is expensive but `run()` is thread-safe. One long-lived instance, explicit lifecycle, idiomatic for native-backed Java libs. Avoids static singleton pitfalls in servlet containers. | — Pending |
| Algorithm analysis is Phase 1, Plan 1 — gates all code-gen plans | Read-before-write is a non-negotiable user preference. Wiring it into the roadmap (not just a README note) ensures it happens. Output: `docs/algorithm-notes.md` capturing Python↔Rust diff. | — Pending |
| groupId placeholder: `io.github.PLACEHOLDER` | Final groupId is TBD — likely coordinated with the upstream Google issue about community-binding naming conventions. Placeholder makes the eventual rename mechanical. | — Pending |
| Phase-1 charset/text-subtype detection: port only if parity requires | Upstream does limited UTF-8/ASCII heuristics for the `txt` fallback. Parity-driven: if the test suite fails without it, we port it. Otherwise deferred. | — Pending |
| Logging: SLF4J API in core, no binding shipped | Library-standard approach; consumer picks the binding. Drop to zero-logging if the core doesn't actually need to log anything. | — Pending |
| Test fixtures: only vendor those with clear Apache 2.0 / public-domain provenance | Upstream `tests_data/basic/` includes a mix. We document per-fixture provenance in `src/test/resources/fixtures/README.md` and skip anything ambiguous. | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-24 after initialization*
