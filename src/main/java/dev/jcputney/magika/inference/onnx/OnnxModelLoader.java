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

package dev.jcputney.magika.inference.onnx;

import dev.jcputney.magika.ModelLoadException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and SHA-256-verifies the bundled {@code standard_v3_3/model.onnx} (MODEL-04, MODEL-05,
 * ALG-12).
 *
 * <p>Uses {@code OnnxModelLoader.class.getResourceAsStream} + {@link InputStream#readAllBytes()} to
 * keep the bytes in-memory (PITFALLS.md §Pitfall 7 — no temp-file extract of the model; works
 * under OSGi / uber-jar shading without path hacks).
 *
 * <p>The expected digest is pinned in {@link #EXPECTED_SHA256}; it MUST match the committed
 * {@code model.onnx} byte-for-byte. The same value appears in {@code docs/MODEL_CARD.md} and
 * {@code docs/algorithm-notes.md} §Model SHA-256 — any drift fails the loader at startup with
 * {@link ModelLoadException}.
 */
public final class OnnxModelLoader {

  /**
   * Logger is keyed on the public {@code dev.jcputney.magika.Magika} facade (Plan 5) so the
   * ERROR-on-SHA-mismatch log event lands on the same logger as the other D-11 events. Consumer
   * log-config hooks {@code dev.jcputney.magika} and catches the ERROR on SHA mismatch (D-11).
   */
  private static final Logger LOGGER = LoggerFactory.getLogger("dev.jcputney.magika.Magika");

  /** Absolute classpath path to the bundled model (D-01 vendoring target). */
  public static final String MODEL_RESOURCE =
    "/dev/jcputney/magika/models/standard_v3_3/model.onnx";

  /**
   * SHA-256 of the committed {@code model.onnx} bytes at the pinned upstream SHA
   * {@code 363a44183a6f300d5d7143d94a19e6a841671650}. Mirrors {@code docs/MODEL_CARD.md} and
   * {@code docs/algorithm-notes.md} §Model SHA-256; all three must match.
   */
  public static final String EXPECTED_SHA256 =
    "fe2d2eb49c5f88a9e0a6c048e15d6ffdf86235519c2afc535044de433169ec8c";

  private OnnxModelLoader() {
    // utility
  }

  /**
   * Bytes + SHA-256 digest of the bundled model, computed once. (DEBT-02 IN-01)
   *
   * <p>Returned by {@link #load()} so callers consume the verified digest without recomputing it.
   * Cite RESEARCH §IN-01 — Magika constructor previously called both {@code loadAndVerify()} (which
   * computes SHA internally to verify) and {@code computeSha256(bytes)} again to populate
   * {@code modelSha256} for the D-11 INFO log.
   */
  public record LoadedModel(byte[] bytes, String sha256) {}

  /**
   * Loads and verifies the bundled model, returning bytes AND the verified SHA-256 in one call so
   * callers do not recompute the digest. (DEBT-02 IN-01 — closes the redundant-compute site in
   * {@code Magika} constructor.) On SHA mismatch, emits ERROR log (D-11) and throws
   * {@link ModelLoadException}.
   *
   * @return the bundled model bytes paired with the verified lowercase-hex SHA-256
   * @throws ModelLoadException if the resource is missing, unreadable, or the SHA-256 does not
   *                            match
   */
  public static LoadedModel load() {
    byte[] bytes;
    try (InputStream in = OnnxModelLoader.class.getResourceAsStream(MODEL_RESOURCE)) {
      if (in == null) {
        throw new ModelLoadException("bundled model missing from classpath: " + MODEL_RESOURCE);
      }
      bytes = in.readAllBytes();
    } catch (IOException e) {
      throw new ModelLoadException("failed to read bundled model: " + MODEL_RESOURCE, e);
    }
    String actual = computeSha256(bytes);
    if (!actual.equalsIgnoreCase(EXPECTED_SHA256)) {
      LOGGER.error("model SHA-256 mismatch: expected={} actual={}", EXPECTED_SHA256, actual);
      throw new ModelLoadException(
        "model SHA-256 mismatch: expected " + EXPECTED_SHA256 + " got " + actual);
    }
    return new LoadedModel(bytes, actual);
  }

  /**
   * Load bytes from the bundled classpath resource and verify the SHA-256 matches
   * {@link #EXPECTED_SHA256}. On mismatch, emits ERROR log (D-11) and throws
   * {@link ModelLoadException}.
   *
   * <p>Back-compat wrapper around {@link #load()}; new callers should prefer {@link #load()} which
   * exposes the verified SHA-256 alongside the bytes (DEBT-02 IN-01).
   *
   * @return the bundled model bytes
   * @throws ModelLoadException if the resource is missing, unreadable, or the SHA-256 does not
   *                            match
   */
  public static byte[] loadAndVerify() {
    byte[] bytes;
    try (InputStream in = OnnxModelLoader.class.getResourceAsStream(MODEL_RESOURCE)) {
      if (in == null) {
        throw new ModelLoadException("bundled model missing from classpath: " + MODEL_RESOURCE);
      }
      bytes = in.readAllBytes();
    } catch (IOException e) {
      throw new ModelLoadException("failed to read bundled model: " + MODEL_RESOURCE, e);
    }
    String actual = computeSha256(bytes);
    if (!actual.equalsIgnoreCase(EXPECTED_SHA256)) {
      LOGGER.error("model SHA-256 mismatch: expected={} actual={}", EXPECTED_SHA256, actual);
      throw new ModelLoadException(
        "model SHA-256 mismatch: expected " + EXPECTED_SHA256 + " got " + actual);
    }
    return bytes;
  }

  /** Compute the SHA-256 digest of the given bytes as lowercase hex. */
  public static String computeSha256(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new ModelLoadException("SHA-256 not available in this JVM", e);
    }
  }
}
