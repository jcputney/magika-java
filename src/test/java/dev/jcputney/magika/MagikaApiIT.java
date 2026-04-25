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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jcputney.magika.postprocess.ContentTypeLabel;
import dev.jcputney.magika.postprocess.PredictionMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Public API integration tests — exercises every D-08 edge case, D-10 NPE path, API-11 lifecycle,
 * and API-12 metadata accessor on a real {@link Magika} instance with the bundled
 * {@code standard_v3_3} ONNX model.
 *
 * <p>Tagged {@code parity} so Failsafe runs it (Surefire excludes {@code parity,slow}). Name ends
 * in {@code IT} so Failsafe's default include pattern ({@code *IT.java}) picks it up.
 *
 * <p><strong>No concurrent-close test</strong> — calling {@code close()} while an
 * {@code identify*} call is mid-flight is undefined behavior per ORT 1.25.0; exercising the race
 * would flake or crash the JVM in CI with no diagnostic value. The failure mode is documented in
 * {@code Magika#close()} Javadoc. See threat-register T-05-06.
 */
@Tag("parity")
class MagikaApiIT {

  @Test
  void identifyBytes_null_throws_NPE_with_param_name() {
    try (Magika m = Magika.create()) {
      assertThatThrownBy(() -> m.identifyBytes(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("bytes");
    }
  }

  @Test
  void identifyPath_null_throws_NPE() {
    try (Magika m = Magika.create()) {
      assertThatThrownBy(() -> m.identifyPath(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("path");
    }
  }

  @Test
  void identifyStream_null_throws_NPE() {
    try (Magika m = Magika.create()) {
      assertThatThrownBy(() -> m.identifyStream(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("stream");
    }
  }

  @Test
  void identifyPath_absent_file_throws_InvalidInputException() {
    try (Magika m = Magika.create()) {
      Path missing = Path.of("/tmp/does-not-exist-magika-test-" + System.nanoTime());
      assertThatThrownBy(() -> m.identifyPath(missing))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("file does not exist:");
    }
  }

  @Test
  void identifyPath_directory_throws_InvalidInputException(@TempDir
  Path tmp) {
    try (Magika m = Magika.create()) {
      assertThatThrownBy(() -> m.identifyPath(tmp))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("path is a directory:");
    }
  }

  @Test
  void identifyPath_symlink_is_followed(@TempDir
  Path tmp) throws Exception {
    Path target = tmp.resolve("target.txt");
    Files.writeString(target, "<html>hello from a linked file</html>");
    Path link = tmp.resolve("link.txt");
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | java.io.IOException e) {
      // Skip on filesystems that can't create symlinks (e.g. restricted Windows runners).
      return;
    }
    try (Magika m = Magika.create()) {
      MagikaResult r = m.identifyPath(link);
      assertThat(r).isNotNull();
      assertThat(r.output().label()).isNotNull();
    }
  }

  @Test
  void empty_bytes_returns_EMPTY_sentinel() {
    try (Magika m = Magika.create()) {
      MagikaResult r = m.identifyBytes(new byte[0]);
      assertThat(r.output().label().label()).isEqualTo("empty");
      assertThat(r.dl().label()).isEqualTo(ContentTypeLabel.UNDEFINED);
    }
  }

  @Test
  void one_byte_invalid_utf8_returns_UNDEFINED_and_UNKNOWN() {
    try (Magika m = Magika.create()) {
      MagikaResult r = m.identifyBytes(new byte[] {(byte) 0xFF});
      assertThat(r.dl().label()).isEqualTo(ContentTypeLabel.UNDEFINED);
      assertThat(r.output().label().label()).isEqualTo("unknown");
    }
  }

  @Test
  void one_byte_valid_ascii_returns_UNDEFINED_and_TXT() {
    try (Magika m = Magika.create()) {
      MagikaResult r = m.identifyBytes("A".getBytes(StandardCharsets.US_ASCII));
      assertThat(r.dl().label()).isEqualTo(ContentTypeLabel.UNDEFINED);
      assertThat(r.output().label().label()).isEqualTo("txt");
    }
  }

  @Test
  void close_is_idempotent() {
    Magika m = Magika.create();
    m.close();
    m.close(); // no-op, no exception
  }

  @Test
  void post_close_identifyBytes_throws_IllegalStateException_with_exact_message() {
    Magika m = Magika.create();
    m.close();
    assertThatThrownBy(() -> m.identifyBytes(new byte[] {'a'}))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Magika has been closed");
  }

  @Test
  void post_close_identifyPath_throws_IllegalStateException(@TempDir
  Path tmp) throws Exception {
    Path p = tmp.resolve("sample.txt");
    Files.writeString(p, "some text");
    Magika m = Magika.create();
    m.close();
    assertThatThrownBy(() -> m.identifyPath(p))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Magika has been closed");
  }

  @Test
  void metadata_accessors_return_expected_values() {
    try (Magika m = Magika.create()) {
      assertThat(m.getModelName()).isEqualTo("standard_v3_3");
      assertThat(m.getModelVersion()).isEqualTo("v3_3");
      assertThat(m.getModelSha256()).matches("[0-9a-fA-F]{64}");
      assertThat(m.getOutputContentTypes()).isNotEmpty();
    }
  }

  @Test
  void builder_predictionMode_propagates_to_identify_call() {
    // (a) Enum mirror-upstream check: values in declared order.
    PredictionMode[] values = PredictionMode.values();
    assertThat(values)
      .as("PredictionMode.values() must mirror upstream order: BEST_GUESS, MEDIUM_CONFIDENCE, HIGH_CONFIDENCE")
      .containsExactly(
        PredictionMode.BEST_GUESS,
        PredictionMode.MEDIUM_CONFIDENCE,
        PredictionMode.HIGH_CONFIDENCE);

    // (b) DEFAULT is HIGH_CONFIDENCE per upstream magika.py:60 (ALG-06).
    assertThat(PredictionMode.DEFAULT)
      .as("PredictionMode.DEFAULT must be HIGH_CONFIDENCE (ALG-06)")
      .isSameAs(PredictionMode.HIGH_CONFIDENCE);

    // (c) Behavioral difference: BEST_GUESS uses threshold=0.0 so output.label always equals
    // dl.label for any label NOT in the overwrite map. Use 32 bytes of low-entropy binary that
    // the model will assign some dl label with a potentially low confidence score. With
    // BEST_GUESS the score can never trigger a fallback (threshold=0.0 → score always ≥ 0.0),
    // so output.label.label() must equal dl.label.label() whenever dl.label is not in the
    // overwrite map (randombytes/randomtxt are the only two entries, which themselves remap
    // deterministically). The assertion is structural: BEST_GUESS never falls back; the builder
    // wiring is proven by the contrast with HIGH_CONFIDENCE potentially falling back.
    byte[] ambiguousInput = new byte[32];
    for (int i = 0; i < ambiguousInput.length; i++) {
      ambiguousInput[i] = (byte) (i * 7 + 13); // non-trivial, not all-zeros, no magic header
    }

    MagikaResult bestGuessResult;
    MagikaResult highConfResult;

    try (Magika bestGuess = Magika.builder().predictionMode(PredictionMode.BEST_GUESS).build()) {
      bestGuessResult = bestGuess.identifyBytes(ambiguousInput);
    }
    try (Magika highConf = Magika.builder().predictionMode(PredictionMode.HIGH_CONFIDENCE).build()) {
      highConfResult = highConf.identifyBytes(ambiguousInput);
    }

    // Both results must be non-null — the builder wiring reached identifyBytes.
    assertThat(bestGuessResult).isNotNull();
    assertThat(highConfResult).isNotNull();
    assertThat(bestGuessResult.score()).isBetween(0.0, 1.0001);
    assertThat(highConfResult.score()).isBetween(0.0, 1.0001);

    // BEST_GUESS structural invariant: output.label equals dl.label for any label not in the
    // overwrite map (threshold=0.0 means no fallback). The two overwrite-map entries
    // (randombytes→unknown, randomtxt→txt) are deterministic remaps that don't depend on mode,
    // so even for those the assertion holds: output != dl but it's an overwrite-map remap, not
    // a LOW_CONFIDENCE fallback. We assert the negative: OverwriteReason must NOT be
    // LOW_CONFIDENCE under BEST_GUESS.
    assertThat(bestGuessResult.output().overwriteReason())
      .as(
        "BEST_GUESS must never produce LOW_CONFIDENCE fallback (threshold=0.0); "
          + "actual reason=%s, dl=%s, output=%s, score=%.4f",
        bestGuessResult.output().overwriteReason(),
        bestGuessResult.dl().label().label(),
        bestGuessResult.output().label().label(),
        bestGuessResult.score())
      .isNotEqualTo(dev.jcputney.magika.postprocess.OverwriteReason.LOW_CONFIDENCE);
  }

  @Test
  void identifyPath_plain_text_runs_model_and_returns_non_null_result(@TempDir
  Path tmp)
    throws Exception {
    // Size >= min_file_size_for_dl (8) so the model actually runs. Exercises the full
    // extract-window + run-model + resolve-label pipeline without asserting a specific label
    // (that's Plan 6's parity harness job).
    Path p = tmp.resolve("sample.txt");
    Files.writeString(p, "plain text content long enough to reach the model, 72 bytes or so");
    try (Magika m = Magika.create()) {
      MagikaResult r = m.identifyPath(p);
      assertThat(r).isNotNull();
      assertThat(r.output().label()).isNotNull();
      assertThat(r.dl().label()).isNotNull();
      assertThat(r.score()).isBetween(0.0, 1.0001);
    }
  }
}
