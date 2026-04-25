/*
 * Copyright 2026 Jonathan Putney
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.jcputney.magika;

import dev.jcputney.magika.config.ContentTypeRegistry;
import dev.jcputney.magika.config.MagikaConfigLoader;
import dev.jcputney.magika.config.ThresholdConfig;
import dev.jcputney.magika.inference.InferenceEngine;
import dev.jcputney.magika.inference.InferenceResult;
import dev.jcputney.magika.inference.onnx.OnnxInferenceEngine;
import dev.jcputney.magika.inference.onnx.OnnxModelLoader;
import dev.jcputney.magika.io.ByteWindowExtractor;
import dev.jcputney.magika.io.BytesInput;
import dev.jcputney.magika.io.InputSource;
import dev.jcputney.magika.io.PathInput;
import dev.jcputney.magika.postprocess.ContentTypeLabel;
import dev.jcputney.magika.postprocess.FallbackLogic;
import dev.jcputney.magika.postprocess.LabelResolver;
import dev.jcputney.magika.postprocess.OverwriteReason;
import dev.jcputney.magika.postprocess.PredictionMode;
import dev.jcputney.magika.postprocess.ResolvedLabels;
import dev.jcputney.magika.postprocess.Utf8Validator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-type detector — independent community Java binding of Google Magika.
 *
 * <h2>Lifecycle</h2>
 * Construction is expensive (hundreds of ms — loads the ONNX model and creates an
 * {@code ai.onnxruntime.OrtSession}). A {@code Magika} instance is thread-safe for
 * {@link #identifyBytes}, {@link #identifyPath}, and {@link #identifyStream}; construction and
 * {@link #close()} are NOT thread-safe. Construct once, share across threads, close at shutdown.
 *
 * <h2>Exception model</h2>
 * All failures surface as unchecked {@link MagikaException} subtypes —
 * {@link ModelLoadException}, {@link InferenceException}, or {@link InvalidInputException}. The
 * underlying ONNX Runtime {@code OrtException} is wrapped and never leaks (API-09). {@code null}
 * inputs throw {@link NullPointerException} (caller bug, not a runtime condition — D-10).
 *
 * <h2>Attribution</h2>
 * Independent community binding — not official, not endorsed by Google. Ships the upstream
 * {@code standard_v3_3} model under Apache 2.0. See {@code docs/MODEL_CARD.md} for provenance.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * try (Magika m = Magika.create()) {
 *   MagikaResult r = m.identifyPath(Path.of("/tmp/example.zip"));
 *   System.out.println(r.output().label().label() + " (" + r.score() + ")");
 * }
 * }</pre>
 */
public final class Magika implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(Magika.class);

  private static final String MODEL_NAME = "standard_v3_3";
  private static final String MODEL_VERSION = "v3_3";

  private final InferenceEngine engine;
  private final ThresholdConfig config;
  private final ContentTypeRegistry registry;
  private final PredictionMode mode;
  private final String modelSha256;
  private final List<ContentTypeLabel> outputContentTypes;
  private volatile boolean closed = false;

  Magika(MagikaBuilder builder) {
    Objects.requireNonNull(builder, "builder");
    long startMs = System.currentTimeMillis();
    this.config = MagikaConfigLoader.loadBundled();
    this.registry = MagikaConfigLoader.loadBundledRegistry();
    this.mode = builder.predictionMode();

    byte[] modelBytes = OnnxModelLoader.loadAndVerify();
    this.modelSha256 = OnnxModelLoader.computeSha256(modelBytes);

    int expectedTokens = config.begSize() + config.midSize() + config.endSize();
    this.engine = new OnnxInferenceEngine(modelBytes, expectedTokens, config.targetLabelsSpace());

    this.outputContentTypes = config.targetLabelsSpace().stream()
      .map(label -> new ContentTypeLabel(label, registry.get(label)))
      .collect(Collectors.toUnmodifiableList());

    long loadMs = System.currentTimeMillis() - startMs;
    // D-11 INFO: load event (5 fields) — fires exactly once per instance.
    LOGGER.info(
      "Magika loaded: name={} version={} sha256={} contentTypeCount={} loadMs={}",
      MODEL_NAME,
      MODEL_VERSION,
      modelSha256,
      outputContentTypes.size(),
      loadMs);
  }

  /** Zero-arg convenience (API-01) — equivalent to {@code Magika.builder().build()}. */
  public static Magika create() {
    return builder().build();
  }

  /** Returns a new builder (API-01). */
  public static MagikaBuilder builder() {
    return new MagikaBuilder();
  }

  /**
   * Identifies the content type of a byte array (API-02).
   *
   * <p><b>Thread safety:</b> Safe to call concurrently across threads on a single
   * {@code Magika} instance (API-10).
   *
   * @param bytes the bytes to classify (not null — D-10)
   * @return the detection result
   * @throws NullPointerException  if {@code bytes} is null (D-10)
   * @throws IllegalStateException if this instance has been closed (API-11)
   */
  public MagikaResult identifyBytes(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    checkOpen();
    return identifyInternal(new BytesInput(bytes), bytes, bytes.length);
  }

  /**
   * Identifies the content type of a file (API-03, D-08).
   *
   * <p>Edge cases:
   * <ul>
   * <li>Absent file → {@link InvalidInputException} wrapping {@link NoSuchFileException}.
   * <li>Directory path → {@link InvalidInputException}.
   * <li>Symlink → followed (matches upstream Python + {@code Files.readAllBytes}).
   * <li>IO error → wrapped to {@link InvalidInputException} (API-09; no {@code IOException}
   * leaks).
   * </ul>
   *
   * <p><b>Thread safety:</b> Safe to call concurrently (API-10).
   *
   * @param path path to the file to classify (not null — D-10)
   * @return the detection result
   * @throws NullPointerException  if {@code path} is null (D-10)
   * @throws InvalidInputException if the path is absent, a directory, or unreadable (D-08)
   * @throws IllegalStateException if this instance has been closed (API-11)
   */
  public MagikaResult identifyPath(Path path) {
    Objects.requireNonNull(path, "path");
    checkOpen();
    try {
      if (!Files.exists(path)) {
        throw new InvalidInputException(
          "file does not exist: " + path, new NoSuchFileException(path.toString()));
      }
      if (Files.isDirectory(path)) {
        throw new InvalidInputException(
          "path is a directory: " + path, new IOException("directory: " + path));
      }
      long size = Files.size(path);
      byte[] content = size < config.minFileSizeForDl() ? Files.readAllBytes(path) : null;
      int effectiveLen =
        content == null ? (int) Math.min(size, Integer.MAX_VALUE) : content.length;
      return identifyInternal(new PathInput(path), content, effectiveLen);
    } catch (InvalidInputException iie) {
      throw iie;
    } catch (IOException e) {
      throw new InvalidInputException("failed to read path: " + path, e);
    }
  }

  /**
   * Identifies the content type of an {@link InputStream} (API-04, D-09).
   *
   * <p><b>Stream contract (D-09):</b> The implementation buffers enough bytes to cover all three
   * slices (beg, mid, end) per {@code docs/algorithm-notes.md} §"Stream handling". An
   * implementation that only reads {@code beg_size} bytes would silently break parity on formats
   * where the signal lives in the tail (ZIP end-of-central-directory, TAR trailer, PDF
   * {@code %%EOF} trailer). For non-seekable streams this means reading to EOF into a byte
   * buffer.
   *
   * <p>The stream is <strong>NOT</strong> closed by this call — the caller owns
   * {@code close()}. Post-return stream position is undefined; callers needing reuse should pass
   * {@link java.io.ByteArrayInputStream} or re-open a {@link java.io.FileInputStream}.
   *
   * <p><b>Thread safety:</b> Safe to call concurrently across threads on a single
   * {@code Magika} instance (API-10), but the caller's stream must be thread-confined — an
   * {@link InputStream} is generally not thread-safe to read concurrently.
   *
   * @param stream the stream to classify (not null — D-10)
   * @return the detection result
   * @throws NullPointerException  if {@code stream} is null (D-10)
   * @throws InvalidInputException if stream read fails
   * @throws IllegalStateException if this instance has been closed (API-11)
   */
  public MagikaResult identifyStream(InputStream stream) {
    Objects.requireNonNull(stream, "stream");
    checkOpen();
    // CR-01 fix: materialize the stream into a byte[] BEFORE entering identifyInternal so the
    // small-file short-circuit (Pitfall 4 / POST-03) fires for empty / N<min_file_size_for_dl
    // streams. Without this, an empty or 1..7-byte stream would skip the smallBuffer guard
    // (smallBuffer was null for the stream path) and run the model on all-padding tokens —
    // disagreeing with upstream Python which returns the EMPTY / TXT / UNKNOWN sentinel without
    // invoking the model. D-09 still holds: ByteWindowExtractor.buildFromStream also buffers to
    // EOF, but the small-file branch is post-strip-aware via knownLength.
    byte[] buffered;
    try {
      buffered = stream.readAllBytes();
    } catch (IOException e) {
      throw new InvalidInputException("failed to read stream", e);
    }
    return identifyInternal(new BytesInput(buffered), buffered, buffered.length);
  }

  /** Returns the bundled model name (API-12). */
  public String getModelName() {
    return MODEL_NAME;
  }

  /** Returns the bundled model version (API-12). */
  public String getModelVersion() {
    return MODEL_VERSION;
  }

  /**
   * Returns the bundled model SHA-256 (diagnostic — matches {@code docs/MODEL_CARD.md}). The
   * same value is emitted in the D-11 INFO-on-load log event.
   */
  public String getModelSha256() {
    return modelSha256;
  }

  /** Returns the list of {@link ContentTypeLabel}s the bundled model can emit (API-12). */
  public List<ContentTypeLabel> getOutputContentTypes() {
    return outputContentTypes;
  }

  /**
   * Closes the underlying {@link InferenceEngine} (API-11). Idempotent — subsequent calls are
   * no-ops. After closure, any {@code identify*} call throws {@link IllegalStateException} with
   * message {@code "Magika has been closed"}.
   *
   * <p><b>Not thread-safe</b> with concurrent {@code identify*} calls.
   *
   * <p><strong>Concurrency:</strong> Calling {@code close()} while another thread is mid-flight
   * inside an {@code identify*} call is undefined behavior. The JVM may crash with a
   * "native access violation" because the in-flight {@code OrtSession.run()} holds native
   * resources that {@code close()} disposes; ORT 1.25.0 does not synchronize these. Callers must
   * ensure all {@code identify*} calls have returned before invoking {@code close()}. Typical
   * patterns: (1) use try-with-resources in a single-threaded context, or (2) coordinate
   * shutdown via an external latch that drains in-flight work before {@code close()} runs.
   */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    engine.close();
    // Do NOT close OrtEnvironment — process-wide singleton.
    // D-11 INFO: close event — fires exactly once per instance (idempotency guard above).
    LOGGER.info("Magika closed: name={} version={} sha256={}", MODEL_NAME, MODEL_VERSION, modelSha256);
  }

  private void checkOpen() {
    if (closed) {
      throw new IllegalStateException("Magika has been closed"); // D-08 / API-11
    }
  }

  private MagikaResult identifyInternal(InputSource src, byte[] smallBuffer, int knownLength) {
    // Small-file short-circuit (Pitfall 4 / POST-03): skip the model for empty files and files
    // below min_file_size_for_dl. FallbackLogic returns null when N >= min_file_size_for_dl,
    // signaling "run the model"; otherwise it returns a ResolvedLabels with UNDEFINED dl.
    if (smallBuffer != null && knownLength < config.minFileSizeForDl()) {
      ResolvedLabels small =
        FallbackLogic.smallFileBranch(smallBuffer, knownLength, config, registry);
      if (small != null) {
        return toResult(small);
      }
    }

    int[] tokens;
    try {
      tokens = ByteWindowExtractor.extract(src, config);
    } catch (IOException e) {
      throw new InvalidInputException("failed to extract window", e);
    }

    // CR-02 / algorithm-notes §"Small-file branches" row 3 (magika.py:756-770):
    // Even when N >= min_file_size_for_dl, if the post-strip beg window has fewer than
    // min_file_size_for_dl meaningful tokens, upstream Python skips the model and falls back to
    // the raw-leading-block UTF-8 decode. Detection: tokens[min_file_size_for_dl - 1] is the
    // padding sentinel (no real byte survived strip at that index). Sentinel selection: TXT if
    // the raw leading block decodes as strict UTF-8, else UNKNOWN. Upstream uses the raw
    // unstripped leading block (`seekable.read_at(0, min(N, block_size))`), NOT the stripped
    // bytes — see magika.py:_get_label_from_few_bytes called via _get_result_from_few_bytes.
    int minSize = config.minFileSizeForDl();
    if (tokens[minSize - 1] == config.paddingToken()) {
      byte[] rawLeading;
      try {
        rawLeading = readRawLeadingBlock(src, smallBuffer);
      } catch (IOException e) {
        throw new InvalidInputException("failed to read leading block", e);
      }
      ContentTypeLabel output =
        Utf8Validator.isValid(rawLeading, 0, rawLeading.length) ? ContentTypeLabel.TXT : ContentTypeLabel.UNKNOWN;
      ResolvedLabels resolved =
        new ResolvedLabels(ContentTypeLabel.UNDEFINED, output, 1.0, OverwriteReason.NONE);
      return toResult(resolved);
    }

    InferenceResult raw = engine.run(tokens); // OnnxInferenceEngine wraps OrtException internally
    ResolvedLabels resolved = LabelResolver.resolve(raw, config, mode, registry);

    // D-11: NO per-call log site here. The overwrite-hit DEBUG log was removed in 01-07 (WR-01)
    // because per-call logging payloads violate the D-11 three-event contract (load/close/error)
    // and CLAUDE.md's explicit "no per-call logging payloads" out-of-scope item. Consumers needing
    // overwrite visibility inspect MagikaResult.output().overwriteReason() programmatically.

    return toResult(resolved);
  }

  /**
   * CR-02 helper. Read the raw unstripped leading block (up to {@code min(N, block_size)} bytes)
   * for the post-token "stripped content too short" branch. Mirrors upstream Python
   * {@code seekable.read_at(0, min(seekable.size, block_size))} at {@code magika.py:760-763} —
   * the bytes the {@code _get_label_from_few_bytes} UTF-8 decode operates on.
   */
  private byte[] readRawLeadingBlock(InputSource src, byte[] smallBuffer) throws IOException {
    int blockSize = config.blockSize();
    if (smallBuffer != null) {
      int n = Math.min(smallBuffer.length, blockSize);
      if (n == smallBuffer.length) {
        return smallBuffer;
      }
      byte[] out = new byte[n];
      System.arraycopy(smallBuffer, 0, out, 0, n);
      return out;
    }
    if (src instanceof PathInput p) {
      long size = Files.size(p.path());
      int n = (int) Math.min((long) blockSize, size);
      byte[] out = new byte[n];
      try (java.io.InputStream in = Files.newInputStream(p.path())) {
        int off = 0;
        while (off < n) {
          int read = in.read(out, off, n - off);
          if (read < 0) {
            break;
          }
          off += read;
        }
      }
      return out;
    }
    // BytesInput / StreamInput should always have smallBuffer set after CR-01 routing; if we get
    // here without smallBuffer, the InputSource shape is unexpected — surface it loudly.
    throw new IllegalStateException(
      "readRawLeadingBlock: smallBuffer null but src is not PathInput: " + src.getClass());
  }

  private static MagikaResult toResult(ResolvedLabels r) {
    MagikaPrediction dl = new MagikaPrediction(r.dlLabel(), r.score(), OverwriteReason.NONE);
    MagikaPrediction out = new MagikaPrediction(r.outputLabel(), r.score(), r.overwriteReason());
    return new MagikaResult(dl, out, r.score());
  }
}
