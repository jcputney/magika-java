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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OnnxModelLoader}. These tests exercise the loader without creating an
 * {@code OrtSession} — they do not touch ONNX Runtime natives and are safe to run under surefire
 * on any platform. Native-load coverage lives in {@code OnnxInferenceEngineIT} (failsafe).
 */
@Tag("unit")
class OnnxModelLoaderTest {

  @Test
  void computeSha256_matches_known_vector_for_hello() {
    // SHA-256 of "hello" = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
    byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
    assertThat(OnnxModelLoader.computeSha256(bytes))
      .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
  }

  @Test
  void computeSha256_matches_known_vector_for_empty() {
    // SHA-256 of "" = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
    assertThat(OnnxModelLoader.computeSha256(new byte[0]))
      .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }

  @Test
  void loadAndVerify_reads_bundled_model_and_sha_matches() {
    byte[] bytes = OnnxModelLoader.loadAndVerify();
    // Sanity: the bundled standard_v3_3 model is ~3 MB.
    assertThat(bytes.length).isGreaterThan(100_000);
    // SHA must match the constant without a separate call — loadAndVerify would have thrown.
    assertThat(OnnxModelLoader.computeSha256(bytes))
      .isEqualToIgnoringCase(OnnxModelLoader.EXPECTED_SHA256);
  }

  @Test
  void expected_sha256_is_a_64_char_hex_string() {
    // Catches typos / empty placeholders at build time.
    assertThat(OnnxModelLoader.EXPECTED_SHA256).hasSize(64).matches("[0-9a-fA-F]+");
  }

  @Test
  void load_returns_bytes_and_sha256_in_one_call() {
    // IN-01: load() exposes the SHA-256 digest computed inside the loader, so callers
    // (Magika constructor) no longer call computeSha256(loaded.bytes()) separately.
    OnnxModelLoader.LoadedModel loaded = OnnxModelLoader.load();
    assertThat(loaded.bytes().length).isGreaterThan(100_000);
    assertThat(loaded.sha256()).isEqualToIgnoringCase(OnnxModelLoader.EXPECTED_SHA256);
  }
}
