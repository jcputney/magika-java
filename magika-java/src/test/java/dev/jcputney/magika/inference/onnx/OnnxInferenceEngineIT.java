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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jcputney.magika.config.MagikaConfigLoader;
import dev.jcputney.magika.config.ThresholdConfig;
import dev.jcputney.magika.inference.InferenceResult;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * First real ORT native load in the codebase (INF-05, INF-06, INF-07 exercised against the actual
 * {@code standard_v3_3} model bytes).
 *
 * <p>Runs under Failsafe ({@code *IT.java} + {@code @Tag("parity")}); extracted ORT natives take
 * a few hundred ms on first load. Plan 06's CI matrix (Linux x64 / macOS aarch64 / Windows x64)
 * gates this class on every push.
 */
@Tag("parity")
class OnnxInferenceEngineIT {

  @Test
  void loads_bundled_model_and_runs_all_padding_tensor() {
    ThresholdConfig cfg = MagikaConfigLoader.loadBundled();
    int expectedTokens = cfg.begSize() + cfg.midSize() + cfg.endSize();
    try (OnnxInferenceEngine engine =
      OnnxInferenceEngine.fromBundledModel(expectedTokens, cfg.targetLabelsSpace())) {
      int[] allPadding = new int[expectedTokens];
      Arrays.fill(allPadding, cfg.paddingToken());

      InferenceResult result = engine.run(allPadding);

      assertThat(result.probabilities()).hasSize(cfg.targetLabelsSpace().size());
      float sum = 0f;
      for (float p : result.probabilities()) {
        assertThat(Float.isNaN(p) || Float.isInfinite(p))
          .as("probability must be finite")
          .isFalse();
        sum += p;
      }
      // Softmax output sums to ~1.0 within floating-point fuzz.
      assertThat(Math.abs(sum - 1.0)).isLessThan(0.01);

      // Top label must come from the model's vocabulary.
      assertThat(cfg.targetLabelsSpace()).contains(result.topContentTypeLabel());
      assertThat(result.topScore()).isBetween(0.0, 1.0001);
    }
  }

  @Test
  void determinism_across_ten_runs_with_same_input() {
    // Pitfall 2 regression: with BASIC_OPT + intraOpNumThreads=1, scores for the same input
    // must be bit-identical across runs. Flaky output here means one of the two knobs slipped.
    ThresholdConfig cfg = MagikaConfigLoader.loadBundled();
    int expectedTokens = cfg.begSize() + cfg.midSize() + cfg.endSize();
    try (OnnxInferenceEngine engine =
      OnnxInferenceEngine.fromBundledModel(expectedTokens, cfg.targetLabelsSpace())) {
      int[] tokens = new int[expectedTokens];
      byte[] payload = "Hello, world!\n".getBytes(StandardCharsets.US_ASCII);
      for (int i = 0; i < tokens.length; i++) {
        tokens[i] = i < payload.length ? (payload[i] & 0xFF) : cfg.paddingToken();
      }

      float[] first = engine.run(tokens).probabilities();
      for (int i = 0; i < 9; i++) {
        float[] next = engine.run(tokens).probabilities();
        assertThat(next)
          .as("run #%d must match run #1 bit-for-bit", i + 2)
          .isEqualTo(first);
      }
    }
  }

  @Test
  void post_close_run_throws_illegal_state() {
    ThresholdConfig cfg = MagikaConfigLoader.loadBundled();
    int expectedTokens = cfg.begSize() + cfg.midSize() + cfg.endSize();
    OnnxInferenceEngine engine =
      OnnxInferenceEngine.fromBundledModel(expectedTokens, cfg.targetLabelsSpace());
    engine.close();
    int[] tokens = new int[expectedTokens];
    assertThatThrownBy(() -> engine.run(tokens))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("closed");
  }

  @Test
  void idempotent_close() {
    ThresholdConfig cfg = MagikaConfigLoader.loadBundled();
    OnnxInferenceEngine engine =
      OnnxInferenceEngine.fromBundledModel(
        cfg.begSize() + cfg.midSize() + cfg.endSize(), cfg.targetLabelsSpace());
    engine.close();
    engine.close(); // second close is a no-op, must not throw
  }
}
