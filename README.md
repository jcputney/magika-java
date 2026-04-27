# magika-java

Clean-room Java binding for [Google Magika](https://github.com/google/magika)
file-type detection. **Independent community binding — not official, not
endorsed by Google.** Ships the upstream `standard_v3_3` ONNX model under
Apache License 2.0.

[![Maven Central](https://img.shields.io/maven-central/v/dev.jcputney/magika-java.svg)](https://central.sonatype.com/artifact/dev.jcputney/magika-java)

## Quickstart

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

```java
import dev.jcputney.magika.Magika;
import dev.jcputney.magika.MagikaResult;
import java.nio.file.Path;

try (Magika m = Magika.create()) {
  MagikaResult r = m.identifyPath(Path.of("/tmp/example.zip"));
  System.out.println(r.output().label().label() + " (" + r.score() + ")");
}
```

Supports `identifyPath(Path)`, `identifyBytes(byte[])`, and
`identifyStream(InputStream)`.

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
