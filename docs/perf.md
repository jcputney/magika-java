# magika-java — Performance Benchmarks

JMH-based microbenchmarks measuring `Magika` detection latency across the public
entry points (`identifyBytes`, `identifyPath`, `identifyStream`, `identifyPaths`)
plus a cold-first-call benchmark that exercises the lazy-init path.

> **Performance is not a stated requirement.** Parity with upstream Python is the
> zero-tolerance bar; latency is exploratory measurement. These benchmarks exist
> to (a) answer concrete latency questions, (b) catch regressions during local
> development, (c) inform any forward-looking claims in README / release notes.
> They are not run in CI — see [Why no CI gating](#why-no-ci-gating).

---

## Quick start

```bash
# Run every benchmark
mvn -pl magika-java -P perf jmh:benchmark

# Filter by class or method (regex). The pw.krejci plugin's user property is
# jmh.benchmarks (the underlying JMH CLI's -bm flag). Other flag names you might
# guess (-Dbenchmark, -Djmh.includes, -Dincludes) are silently ignored — if you
# see the full suite running for a 30-minute spend, double-check this flag.
mvn -pl magika-java -P perf jmh:benchmark -Djmh.benchmarks=IdentifyBytesBenchmark
mvn -pl magika-java -P perf jmh:benchmark -Djmh.benchmarks='Identify.*Benchmark\.identify.*'

# Reduce iterations for a quick smoke run (overrides the annotation defaults)
mvn -pl magika-java -P perf jmh:benchmark \
  -Djmh.benchmarks=IdentifyBytesBenchmark \
  -Djmh.f=1 -Djmh.wi=2 -Djmh.i=3
```

JMH writes a tabular result to stdout. Add `-Djmh.rf=json -Djmh.rff=target/jmh.json`
to dump structured results for diffing across runs.

---

## What each benchmark measures

| Benchmark | What it measures | Mode | Notes |
|---|---|---|---|
| `IdentifyBytesBenchmark` | Steady-state `identifyBytes(byte[])` latency | AverageTime + Throughput, µs | `@Param` over small (35 B GIF, hits short-circuit branch), medium (~600 B Java source, single-window pipeline), large (10 KB tar, multi-window pipeline). |
| `IdentifyPathBenchmark` | Steady-state `identifyPath(Path)` latency | AverageTime + Throughput, µs | Same three fixtures. After first iteration, OS page cache is warm; **measures warm-cache read, not cold disk I/O**. |
| `IdentifyStreamBenchmark` | Steady-state `identifyStream(InputStream)` latency | AverageTime + Throughput, µs | Per-iteration `ByteArrayInputStream` wrap to isolate stream-buffering overhead from any disk/network read. |
| `IdentifyPathsBatchBenchmark` | Steady-state `identifyPaths(List<Path>)` latency at batch sizes 1, 10, 100 | AverageTime, ms | Reported time is **per batch**, not per file. Divide by `batchSize` for amortized per-file cost. Exercises the default `availableProcessors()` parallelism. |
| `ColdFirstCallBenchmark` | Cold path: `Magika.builder().build()` + first `identifyBytes` + `close` | SingleShotTime, ms | Each iteration creates a fresh `Magika`. Forces the lazy ORT-session creation that REF-04's lazy init defers out of `build()`. A naive `build()`-only benchmark would report ~0 µs and hide regressions in session creation. |

All steady-state benchmarks share `Magika` across iterations via `@State(Scope.Benchmark)` — this mirrors real consumer usage (long-lived instance per the v0.1 lifecycle contract). JIT warmup and lazy init are absorbed during JMH's warmup phase.

---

## Configuration

| Knob | Steady-state value | Cold first-call value | Why |
|---|---|---|---|
| `@Fork` | `value=2, warmups=1` | `value=3, warmups=1` | Multiple forks reveal between-process variance. Cold first-call gets one extra fork because single-shot mode has lower per-iteration sample density. |
| `@Warmup` | 10 iter × 2 s | 5 iter (single-shot) | Steady-state warmup bumped from JMH's defaults to absorb JNI / ORT first-call costs in sub-10 µs paths. |
| `@Measurement` | 10 iter × 1 s | 20 iter (single-shot) | Cold-call iteration count is bumped to compensate for single-shot's lower sample count. |
| `@OutputTimeUnit` | µs (steady-state), ms (batch + cold) | — | Sub-10 µs paths read better in microseconds; batch and cold-call workloads naturally land in milliseconds. |

Both ORT inference settings — `SessionOptions.OptLevel.BASIC_OPT` + `setIntraOpNumThreads(1)` — are inherited from the parity-test pipeline, so benchmark numbers are directly comparable to parity-mode results. Don't change these in the benchmark setup; the project's FP-determinism guard depends on them.

---

## Caveats

### Comparing across machines is meaningless

Absolute latency numbers depend on CPU, memory bandwidth, page-cache state, JVM version, and ambient load. Treat any number as machine-relative. When recording numbers in CHANGELOG / README / posts, include the machine spec (e.g. *"p50 X µs / p95 Y µs on M2 Pro, JDK 17 Temurin, JMH defaults"*).

### Path / stream benchmarks measure warm-OS-cache, not disk I/O

After the first iteration the OS page cache is hot. The reported numbers reflect the relative overhead of `identifyPath` vs `identifyBytes` (or `identifyStream` vs `identifyBytes`) for already-cached files — they do not characterize cold-disk reads, networked filesystems, or container-mount overhead.

### Cold-first-call dominates total cost for short-lived processes

`ColdFirstCallBenchmark` consistently reports hundreds of milliseconds — almost all of that is ORT session creation (model bytes load + ONNX graph construction + native session bring-up), not inference. Short-lived CLI-style consumers pay this once per invocation; long-lived consumers pay it once per JVM. The lazy-init contract (REF-04) ensures `build()` itself is fast; the cost lands on the first `identify*()` call.

### No upstream comparison harness

These benchmarks measure magika-java alone. Comparing to upstream Python's `magika.identify_paths(...)` or to the Rust binary requires a separate harness with matching fixtures, matching ORT versions, matching CPU, and a careful JVM-warmup-vs-Python-startup methodology. That's a follow-up — not in scope here.

### Why no CI gating

GitHub Actions runners are ephemeral shared VMs with multi-tenant CPU contention. Latency numbers vary by ±20-50% run-to-run for reasons unrelated to code changes. Asserting "p95 < N µs" in CI would either flake constantly or be set so loose that real regressions slip through. Local-only measurement, results compared mentally between runs.

---

## Adding a benchmark

1. New file under `magika-java/src/test/java/dev/jcputney/magika/perf/` named `*Benchmark.java`.
2. Annotate the class with `@State`, `@BenchmarkMode`, `@OutputTimeUnit`, `@Fork`, `@Warmup`, `@Measurement` — copy the steady-state preamble from `IdentifyBytesBenchmark` unless you need different defaults.
3. `@Setup` constructs whatever shared state the benchmark needs; `@TearDown` releases it. Reuse `FixtureLoader` for classpath-resource access to the parity fixtures.
4. The `@Benchmark` method's body should return its result (don't trust void bodies — JIT can elide them).
5. `mvn -pl magika-java -P perf test-compile` to verify the JMH annotation processor picks it up. Confirm it appears under `magika-java/target/test-classes/META-INF/BenchmarkList`.
6. `mvn -pl magika-java -P perf jmh:benchmark -Djmh.benchmarks=YourNewBenchmark` for a real run.

---

## Baseline

The numbers below are a one-time reference snapshot, not a CI-verified contract. They exist
so a future maintainer (or future-you) has something concrete to diff against when chasing a
suspected regression. Re-run on the same machine to compare apples-to-apples; numbers will
not match on different hardware.

**Machine:** Apple M4 Max, 14 cores, 36 GB RAM
**OS:** macOS 26.4.1 (build 25E253)
**JDK:** OpenJDK 21.0.5 (Corretto-21.0.5.11.1) — note: source/target is JDK 17, runtime here is 21
**ORT:** 1.25.0 (CPU, single-threaded inference per the project's FP-determinism setting)
**Model:** `standard_v3_3` (3.0 MiB ONNX, SHA-256 `fe2d2eb4…ec8c`)
**Date captured:** 2026-04-28
**Benchmark config:** as committed in this directory (steady-state 10 warmup × 2s + 10 measure × 1s, 2 forks; `IdentifyPathBenchmark` 15 × 2s + 15 × 2s; `ColdFirstCallBenchmark` 5 + 20 single-shot, 3 forks)

```
Benchmark                                  (batchSize)                   (fixture)   Mode  Cnt     Score    Error   Units
IdentifyBytesBenchmark.identifyBytes               N/A  synthetic:1B-sub-threshold   avgt   20     0.023 ±  0.001   us/op
IdentifyBytesBenchmark.identifyBytes               N/A           images/sample.gif   avgt   20  2026.892 ± 50.749   us/op
IdentifyBytesBenchmark.identifyBytes               N/A            code/sample.java   avgt   20  2065.174 ± 61.197   us/op
IdentifyBytesBenchmark.identifyBytes               N/A         archives/sample.tar   avgt   20  2083.011 ± 64.129   us/op
IdentifyPathBenchmark.identifyPath                 N/A           images/sample.gif   avgt   30  2286.975 ± 70.877   us/op
IdentifyPathBenchmark.identifyPath                 N/A            code/sample.java   avgt   30  2163.183 ± 27.783   us/op
IdentifyPathBenchmark.identifyPath                 N/A         archives/sample.tar   avgt   30  2154.303 ± 17.737   us/op
IdentifyStreamBenchmark.identifyStream             N/A           images/sample.gif   avgt   20  2040.937 ± 48.468   us/op
IdentifyStreamBenchmark.identifyStream             N/A            code/sample.java   avgt   20  2064.840 ± 56.584   us/op
IdentifyStreamBenchmark.identifyStream             N/A         archives/sample.tar   avgt   20  2042.685 ± 47.720   us/op
IdentifyPathsBatchBenchmark.identifyPaths            1                         N/A   avgt   20     2.261 ±  0.064   ms/op
IdentifyPathsBatchBenchmark.identifyPaths           10                         N/A   avgt   20     4.872 ±  0.041   ms/op
IdentifyPathsBatchBenchmark.identifyPaths          100                         N/A   avgt   20    37.469 ±  1.076   ms/op
ColdFirstCallBenchmark.coldFirstCall               N/A                         N/A     ss   60     8.209 ±  0.232   ms/op
```

### Headlines

- **Sub-threshold short-circuit:** ~23 ns per call (~42 M ops/sec). The `size < min_file_size_for_dl` check returns `UNDEFINED` without touching the model. Effectively free.
- **Steady-state full-pipeline:** ~2.0–2.1 ms across `identifyBytes` / `identifyStream` and ~2.2 ms for `identifyPath` regardless of file size (within the 35 B – 10 KB range tested). The byte-window pipeline is constant-time over input size in this range because the model always reads the same fixed-size beg/mid/end windows.
- **Batch parallelism:** ~6× speedup at batch=100 (per-file 0.37 ms vs serial 2.26 ms) on a 14-core machine. Saturates well before 100; expect diminishing returns past `availableProcessors()`.
- **Cold first-call (warm JVM, JNI loaded):** ~8.2 ms for `build() + first identifyBytes + close`. **In a fresh JVM**, the very first call additionally pays ~600–700 ms for ORT JNI native-library extraction and `System.load()` — see the per-fork warmup logs (`Magika loaded: ... loadMs=678`).

### Re-run command

To regenerate this baseline on the same machine (~30 minutes):

```bash
mvn -pl magika-java -P perf jmh:benchmark \
  -Djmh.rf=json -Djmh.rff=target/jmh-results/baseline.json
```

To diff against this baseline, capture the new run's JSON and compare programmatically (`jq`-able) or visually scan the score columns. There's no built-in tooling for diffing JMH JSON in this project — keeping it manual until a regression actually bites.
