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

import dev.jcputney.magika.config.ContentTypeRegistry;
import dev.jcputney.magika.config.MagikaConfigLoader;
import dev.jcputney.magika.config.ThresholdConfig;
import dev.jcputney.magika.inference.FakeInferenceEngine;
import dev.jcputney.magika.inference.InferenceResult;
import dev.jcputney.magika.io.ByteWindowExtractor;
import dev.jcputney.magika.io.BytesInput;
import dev.jcputney.magika.postprocess.LabelResolver;
import dev.jcputney.magika.postprocess.OverwriteReason;
import dev.jcputney.magika.postprocess.PredictionMode;
import dev.jcputney.magika.postprocess.ResolvedLabels;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * TEST-10: end-to-end smoke test of the full pipeline with FakeInferenceEngine pinning
 * argmax. Exercises {@code BytesInput → ByteWindowExtractor → FakeInferenceEngine → LabelResolver
 * → ResolvedLabels} — ZERO ONNX on the classpath path. Plan 4 will swap FakeInferenceEngine for
 * OnnxRuntimeInferenceEngine and this test still compiles.
 */
@Tag("unit")
class PipelineSmokeTest {

  @Test
  void full_pipeline_bytes_to_resolved_labels_returns_html() throws IOException {
    ThresholdConfig cfg = MagikaConfigLoader.loadThresholdConfig(
      "/dev/jcputney/magika/config-minimal.json");
    ContentTypeRegistry registry = MagikaConfigLoader.loadRegistry(
      "/dev/jcputney/magika/content-types-minimal.json");

    byte[] bytes = readFixture("/dev/jcputney/magika/fixtures/smoke/hello.txt");
    // Sanity: fixture is >= minFileSizeForDl=8 so FallbackLogic does NOT short-circuit.
    assertThat(bytes.length).isGreaterThanOrEqualTo(cfg.minFileSizeForDl());

    int htmlIndex = cfg.targetLabelsSpace().indexOf("html");
    assertThat(htmlIndex).isGreaterThanOrEqualTo(0);

    try (FakeInferenceEngine engine = FakeInferenceEngine.withArgmax(
      "html", 0.95, cfg.targetLabelsSpace(), htmlIndex)) {

      int[] tokens = ByteWindowExtractor.extract(new BytesInput(bytes), cfg);
      assertThat(tokens.length).isEqualTo(cfg.begSize() + cfg.midSize() + cfg.endSize());

      InferenceResult raw = engine.run(tokens);
      assertThat(raw.topContentTypeLabel()).isEqualTo("html");

      ResolvedLabels result = LabelResolver.resolve(
        raw, cfg, PredictionMode.HIGH_CONFIDENCE, registry);

      assertThat(result.outputLabel().label()).isEqualTo("html");
      assertThat(result.score()).isGreaterThan(0.9);
      assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.NONE);
    }
  }

  private static byte[] readFixture(String resourcePath) throws IOException {
    try (InputStream in = PipelineSmokeTest.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IOException("missing test fixture: " + resourcePath);
      }
      return in.readAllBytes();
    }
  }
}
