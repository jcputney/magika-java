# magika-java — Claude context

Clean-room Java library wrapping Google Magika's ONNX file-type detection model. **Parity with upstream Python is the product.** A detection disagreement on any fixture is a test failure with no tolerance, no `@Disabled`, no softening.

## Where to find context

Planning artifacts live in `.planning/` (gitignored — local-only per user preference):
- `.planning/PROJECT.md` — project context, key decisions, out-of-scope list
- `.planning/REQUIREMENTS.md` — 85 v1 REQ-IDs with traceability to phases
- `.planning/ROADMAP.md` — 4-phase plan (Phase 1 = v0.1 skeleton + parity)
- `.planning/research/` — STACK, FEATURES, ARCHITECTURE, PITFALLS, SUMMARY docs
- `.planning/config.json` — GSD workflow settings

## Non-negotiable constraints

- **Read before write.** Phase 1 Plan 1 is `docs/algorithm-notes.md` — document the exact Python algorithm (tensor dtype, strip byte set, overwrite-map ordering, small-file branches, etc.) before any code in `inference/`, `postprocess/`, `io/`, or `config/` is authored. Git history proves ordering.
- **POJO core.** No Jakarta EE, no Spring, no CDI in the core module.
- **No unapproved dependencies.** New dep = stop and ask. Approved for Phase 1: `com.microsoft.onnxruntime:onnxruntime` 1.25.0, Jackson 2.21.2 (databind), SLF4J 2.0.17 API only, JUnit 6.0.3 + AssertJ 3.27.7, plus standard Maven plugins per `.planning/research/STACK.md`.
- **Parity tests fail loudly.** Every disagreement surfaces fixture name + upstream-expected + our-actual + reproducer. No tolerance, no skipping.
- **Stop and ask** before any non-obvious architectural decision, especially: threshold config loading shape, native-lib platform story, session/model lifecycle, API mutability boundaries.
- **Package isolation.** `ai.onnxruntime.*` references ONLY in `dev.jcputney.magika.inference.onnx.*` (bytecode-level ArchUnit rule). Jackson ONLY in `dev.jcputney.magika.config.*`.

## Coordinates (locked)

- groupId: `dev.jcputney`
- artifactId: `magika-java`
- root package: `dev.jcputney.magika`
- Maven Central namespace verified via DNS TXT on `jcputney.dev` (same pattern as sibling `mjml-java`)

## Supported platforms (Phase 1)

CI-gated: Linux x64, macOS aarch64, Windows x64.
Community-tested but ungated: Linux aarch64.
Explicitly unsupported: Intel Mac (`osx-x64` — ORT 1.25.0 dropped it), Windows ARM64 (`win-aarch64` — ORT never shipped it), Alpine/musl (ORT requires glibc).

## What's out of scope (v1)

CLI module · polyglot detection · custom/user-supplied model loading · fine-tuning hooks · Jakarta EE / Spring / CDI integrations · GPU ORT variant · platform-specific classifier JARs · Maven Central publish (deferred to Phase 3) · plugin/SPI hooks · progress callbacks · per-call logging payloads.

## Working style (from user brief)

- Flag uncertainty explicitly. "I don't know" beats a confident guess.
- Surgical changes — don't refactor unrelated code.
- Do not create helper scripts or CLI utilities.
- Do not soften a failing parity test — investigate every disagreement.

## GSD workflow

This project uses Get-Shit-Done planning. Commands:
- `/gsd-plan-phase 1` — decompose Phase 1 into the 6-plan sequence above
- `/gsd-execute-phase 1` — run phase after plans approved
- `/gsd-progress` — snapshot where we are
- `/gsd-discuss-phase N` — gather context before planning

Config: mode=interactive, granularity=standard, parallelization=true, commit_docs=false (`.planning/` gitignored), model_profile=balanced, research=true, plan_check=true, verifier=true.

## Parity pitfalls to remember

1. **Tensor dtype**: input is token IDs in `[0, 256]` — `byte[]` encodes 256 as `-1`. Use `IntBuffer`, never cast from `byte[]`.
2. **`bytes.lstrip()` byte set**: exactly `{0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20}`. `String.strip/trim/isWhitespace` are all wrong.
3. **FP determinism**: pin `SessionOptions.setOptimizationLevel(BASIC_OPT)` + `setIntraOpNumThreads(1)`. Score tolerance in parity tests is 1e-4.
4. **Overwrite-map ordering**: applied BEFORE threshold check, not after.
5. **Small-file branches**: skip the model, return `ContentTypeLabel.UNDEFINED` sentinel. Never `null`, never `Optional`.
6. **UTF-8 decode**: use `CharsetDecoder.decode()` with `CodingErrorAction.REPORT`. `new String(bytes, UTF_8)` silently replaces invalid bytes and breaks parity.
7. **Lifecycle**: one long-lived `Magika`, thread-safe `identify*`, construct/close not thread-safe. `close()` idempotent; post-close throws `IllegalStateException`, not `OrtException`.
