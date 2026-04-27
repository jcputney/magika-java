# magika-java

Clean-room Java binding for [Google Magika](https://github.com/google/magika)
file-type detection. **Independent community binding — not official, not
endorsed by Google.** Ships the upstream `standard_v3_3` ONNX model under
Apache License 2.0.

[![Maven Central](https://img.shields.io/maven-central/v/dev.jcputney/magika-java.svg)](https://central.sonatype.com/artifact/dev.jcputney/magika-java)

**Parity:** verified label-and-score bit-identical to upstream Python `magika==1.0.2`
(oracle SHA `363a44183a6f...`) across the 35 oracle-pinned fixtures and a 100-file
random real-world sample.

## Getting started

### Install

Maven:

```xml
<dependency>
  <groupId>dev.jcputney</groupId>
  <artifactId>magika-java</artifactId>
  <version>0.3.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'dev.jcputney:magika-java:0.3.0'
```

JPMS module name: `dev.jcputney.magika`. If you consume via the module path,
declare `requires dev.jcputney.magika;` in your `module-info.java`. Only the
public API package is exported — the `inference`, `postprocess`, `io`, `config`,
and `model` subpackages are encapsulated.

### Your first call

```java
import dev.jcputney.magika.Magika;
import dev.jcputney.magika.MagikaResult;
import java.nio.file.Path;

try (Magika m = Magika.create()) {
  MagikaResult r = m.identifyPath(Path.of("/tmp/example.zip"));
  System.out.println(r.output().label().label() + " (" + r.score() + ")");
}
```

`Magika.create()` loads the bundled `standard_v3_3` model and creates the ONNX
Runtime session. **This takes hundreds of milliseconds — construct once and
share** (see [Threading model](#threading-model)).

### Reading the result

`MagikaResult` is a 4-component record:

| Field | Type | Meaning |
|---|---|---|
| `dl()` | `MagikaPrediction` | Raw deep-learning prediction (the model's top label, before any overwrite-map adjustment). |
| `output()` | `MagikaPrediction` | Final consumer-facing prediction after the overwrite map and threshold logic — **this is what you usually want**. |
| `score()` | `double` | Confidence score in `[0, 1]`. Same value as `output().score()`. |
| `status()` | `Status` | Outcome: `OK` on every single-call success; one of `FILE_NOT_FOUND_ERROR` / `PERMISSION_ERROR` / `UNKNOWN` for batch per-file failures (see [Batch detection](#batch-detection)). |

`MagikaPrediction` is a 3-component record exposing `label()`, `score()`, and
`overwriteReason()`. The label name (e.g. `"pdf"`, `"png"`, `"java"`) comes
from `prediction.label().label()` — a `String` matching upstream Python's
content-type vocabulary verbatim (214 labels in the bundled model).

```java
String label    = r.output().label().label();           // "pdf"
double score    = r.score();                            // 0.9999
boolean wasOverwritten = !r.dl().label().label()
    .equals(r.output().label().label());                // true if overwrite-map fired
```

### Detection methods

The library offers four entry points, all on the `Magika` instance:

```java
// 1. By path (the library reads the file off disk for you)
MagikaResult r = m.identifyPath(Path.of("/data/foo.bin"));

// 2. By in-memory bytes
byte[] bytes = Files.readAllBytes(Path.of("/data/foo.bin"));
MagikaResult r = m.identifyBytes(bytes);

// 3. By stream (the stream is fully consumed; reset the caller's stream if needed)
try (InputStream in = Files.newInputStream(Path.of("/data/foo.bin"))) {
  MagikaResult r = m.identifyStream(in);
}

// 4. Batch — process many paths in parallel
List<Path> paths = List.of(Path.of("/data/a.bin"), Path.of("/data/b.bin"));
List<MagikaResult> results = m.identifyPaths(paths);
// or with explicit parallelism:
List<MagikaResult> results = m.identifyPaths(paths, /*parallelism=*/ 4);
```

### Batch detection

`identifyPaths(List<Path>)` and `identifyPaths(List<Path>, int parallelism)`
process many files in parallel using an internal worker pool. Result ordering
matches input ordering. The default parallelism is
`Runtime.getRuntime().availableProcessors()`.

Unlike the single-call paths, **batch never throws** — per-file failures are
captured in the result via `Status`:

| Status | Cause |
|---|---|
| `OK` | Successful detection. |
| `FILE_NOT_FOUND_ERROR` | `NoSuchFileException` reading the path. |
| `PERMISSION_ERROR` | `AccessDeniedException` reading the path. |
| `UNKNOWN` | Any other failure (`IOException`, `InvalidInputException`, `InferenceException`). |

Non-`OK` results carry sentinel `dl` / `output` predictions; do not interpret
their labels.

```java
List<MagikaResult> results = m.identifyPaths(paths);
for (int i = 0; i < paths.size(); i++) {
  MagikaResult r = results.get(i);
  if (r.status() == Status.OK) {
    System.out.println(paths.get(i) + " -> " + r.output().label().label());
  } else {
    System.err.println(paths.get(i) + " failed: " + r.status());
  }
}
```

### Configuration

Use `Magika.builder()` for non-default settings:

```java
import dev.jcputney.magika.Magika;
import dev.jcputney.magika.postprocess.PredictionMode;

try (Magika m = Magika.builder()
        .predictionMode(PredictionMode.HIGH_CONFIDENCE)
        .build()) {
  // ...
}
```

`PredictionMode` controls how aggressively the model promotes a top-1 label
versus falling back to a more generic class:

| Mode | Behavior |
|---|---|
| `HIGH_CONFIDENCE` (default) | Strict thresholds. Falls back to `txt` / `unknown` when the top label's confidence is low. Matches upstream Python's default. |
| `MEDIUM_CONFIDENCE` | Looser thresholds; willing to commit to a specific label more often. |
| `BEST_GUESS` | Always returns the top-1 label, no fallback. |

All three modes are upstream-equivalent — the parity test suite covers each
mode against pinned oracle expectations.

### Errors

The single-call paths (`identifyPath`, `identifyBytes`, `identifyStream`) throw
on failure. All exceptions extend the sealed-ish `MagikaException`:

| Exception | When |
|---|---|
| `InvalidInputException` | Caller-supplied input violates a precondition (e.g. null, unreadable path). |
| `InferenceException` | ONNX Runtime native call failed. |
| `ModelLoadException` | Bundled model resource missing / corrupt / SHA-256 mismatch. |

```java
try (Magika m = Magika.create()) {
  MagikaResult r = m.identifyPath(somePath);
  // ...
} catch (InvalidInputException | InferenceException | ModelLoadException e) {
  // log and decide
}
```

For batch (`identifyPaths`), see [Batch detection](#batch-detection) — failures
become `Status` values rather than exceptions.

### Logging

The library uses [SLF4J](https://www.slf4j.org/) for the three lifecycle log
events (model load, model close, batch start) at `INFO` level. **You must
provide an SLF4J implementation on the classpath** to see anything — the
library ships with the API (`slf4j-api`) only:

```xml
<!-- Maven — pick one -->
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-simple</artifactId>
  <version>2.0.17</version>
</dependency>
<!-- or logback / log4j2 / jul-to-slf4j etc. -->
```

With no implementation on the classpath, SLF4J emits a single warning at
startup and silently drops log events — the library still works, but you
won't see model-load timings or batch-start events.

## Threading model

- **One `Magika` per process.** Construction is expensive
  (hundreds of ms — loads the ONNX model and creates an `OrtSession`).
- **`identify*` is thread-safe.** Share a single instance across worker threads.
- **`close()` is NOT thread-safe.** Only call it once, at shutdown; calling
  `close()` while another thread is mid-flight in `identify*` is undefined
  behavior and may crash the JVM with a native access violation.
- **`Magika.builder()` and `build()` are NOT thread-safe.** Construct once and
  share the returned instance.

## Supported platforms

| Platform                        | Status                                                                                                                                              |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Linux x64                       | CI-gated                                                                                                                                            |
| macOS aarch64 (Apple Silicon)   | CI-gated                                                                                                                                            |
| Windows x64                     | CI-gated                                                                                                                                            |
| Linux aarch64                   | Community-tested (no automated gate)                                                                                                                |
| Intel Mac (`osx-x64`)           | **Unsupported** — ONNX Runtime 1.25.0 dropped this. Downstream users needing Intel Mac can pin ONNX Runtime 1.22.0 in their application.            |
| Windows on ARM64 (`win-aarch64`)| **Unsupported** — ONNX Runtime has never shipped this.                                                                                              |
| Alpine / musl libc              | **Unsupported** — ONNX Runtime requires glibc. Use `gcompat` or a glibc-based base image.                                                           |

## Attribution

This library wraps the `standard_v3_3` ONNX model from
<https://github.com/google/magika> (Apache 2.0). Magika is a research
project by Google; see the [paper on arXiv (2409.13768)](https://arxiv.org/abs/2409.13768).

**This is an independent community binding.** It is not published by
Google, not endorsed by Google, and not part of the official Magika
distribution. See [`docs/MODEL_CARD.md`](./docs/MODEL_CARD.md) for
bundled-model provenance including upstream commit SHA and file SHA-256.

## Breaking changes in v0.2

`MagikaResult` is now a 4-component record — adds `Status status`. Existing 3-arg
canonical constructors (`new MagikaResult(dl, output, score)`) stop compiling.

Single-call paths (`identifyPath`, `identifyBytes`, `identifyStream`) always set
`status = Status.OK` on success and continue to throw their existing
`InvalidInputException` / `InferenceException` / `ModelLoadException` on failure
(no semantic change).

The new `Status` enum is a 4-value verbatim mirror of upstream Python's `Status` —
`OK | FILE_NOT_FOUND_ERROR | PERMISSION_ERROR | UNKNOWN`. The non-OK values are
populated only by the new `identifyPaths(List<Path>)` batch method per the per-file
failure mapping documented in the `Magika` class Javadoc (lands in v0.2 plan 03-02).

This is a pre-1.0 source-break, accepted because no Maven Central publish has
happened (no external consumers exist) and only project-internal test fixtures
construct `MagikaResult` directly. After v0.3 ships to Maven Central, similar
record changes will require a different mitigation strategy.

Note: `CHANGELOG.md` begins at v0.3.0; pre-v0.3 history is captured in git tag
annotations (`v0.1`, `v0.2`) and in this section. The `## Breaking changes in
v0.2` block stays in this README until superseded by a future breaking change
with its own section.

## License

Apache License 2.0. See [`LICENSE`](./LICENSE). Upstream `LICENSE` and
`NOTICE` are vendored verbatim under
`src/main/resources/dev/jcputney/magika/models/standard_v3_3/`.
