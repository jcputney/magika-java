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

package dev.jcputney.magika.postprocess;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jcputney.magika.ContentTypeLabel;
import dev.jcputney.magika.OverwriteReason;
import dev.jcputney.magika.PredictionMode;
import dev.jcputney.magika.config.ContentTypeInfo;
import dev.jcputney.magika.config.ContentTypeRegistry;
import dev.jcputney.magika.config.ThresholdConfig;
import dev.jcputney.magika.inference.InferenceResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class LabelResolverTest {

  private static final ThresholdConfig CFG = new ThresholdConfig(
    1024, 0, 1024, 4096, 256, 8,
    0.5,
    false,
    List.of("unknown", "empty", "undefined", "txt", "html", "zip", "randombytes", "randomtxt",
      "markdown"),
    Map.of("markdown", 0.75, "handlebars", 0.9, "crt", 0.9),
    Map.of("randombytes", "unknown", "randomtxt", "txt"),
    3);

  private static final ContentTypeRegistry REGISTRY = ContentTypeRegistry.fromList(List.of(
    new ContentTypeInfo("html", "code", "text/html", "HTML", List.of("html"), true),
    new ContentTypeInfo("zip", "archive", "application/zip", "ZIP", List.of("zip"), false),
    new ContentTypeInfo("txt", "text", "text/plain", "Plain text", List.of("txt"), true),
    new ContentTypeInfo("unknown", "unknown", "application/octet-stream", "Unknown",
      List.of(), false),
    new ContentTypeInfo("markdown", "text", "text/markdown", "Markdown",
      List.of("md"), true),
    new ContentTypeInfo("randombytes", "unknown", "application/octet-stream", "Random bytes",
      List.of(), false),
    new ContentTypeInfo("randomtxt", "text", "text/plain", "Random text",
      List.of(), true)));

  @Test
  void above_threshold_with_no_overwrite() {
    InferenceResult raw = new InferenceResult(new float[] {0.9f}, "html", 0.9);
    ResolvedLabels result = LabelResolver.resolve(
      raw, CFG, PredictionMode.HIGH_CONFIDENCE, REGISTRY);

    assertThat(result.outputLabel().label()).isEqualTo("html");
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.NONE);
  }

  @Test
  void below_threshold_text_fallback() {
    // markdown threshold = 0.75; score 0.6 → below. markdown.isText() = true → TXT fallback.
    InferenceResult raw = new InferenceResult(new float[] {0.6f}, "markdown", 0.6);
    ResolvedLabels result = LabelResolver.resolve(
      raw, CFG, PredictionMode.HIGH_CONFIDENCE, REGISTRY);

    assertThat(result.outputLabel().label()).isEqualTo("txt");
    assertThat(result.outputLabel()).isSameAs(ContentTypeLabel.TXT);
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.LOW_CONFIDENCE);
  }

  @Test
  void below_threshold_unknown_fallback() {
    // zip isn't in per-type thresholds → 0.5 fallback; score 0.3 → below.
    // zip.isText() = false → UNKNOWN fallback.
    InferenceResult raw = new InferenceResult(new float[] {0.3f}, "zip", 0.3);
    ResolvedLabels result = LabelResolver.resolve(
      raw, CFG, PredictionMode.HIGH_CONFIDENCE, REGISTRY);

    assertThat(result.outputLabel().label()).isEqualTo("unknown");
    assertThat(result.outputLabel()).isSameAs(ContentTypeLabel.UNKNOWN);
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.LOW_CONFIDENCE);
  }

  /**
   * Pitfall 5 regression: overwrite-map must be consulted BEFORE the threshold check.
   * randombytes → unknown override must apply even when score is well below threshold.
   */
  @Test
  void overwrite_map_applied_BEFORE_threshold() {
    InferenceResult raw = new InferenceResult(new float[] {0.01f}, "randombytes", 0.01);
    ResolvedLabels result = LabelResolver.resolve(
      raw, CFG, PredictionMode.HIGH_CONFIDENCE, REGISTRY);

    // Per algorithm-notes §Overwrite-map ordering step 2: overwrite applies first.
    // Step 4: score 0.01 < threshold 0.5 → LOW_CONFIDENCE recomputes from output_label "unknown"
    //   which is not text → fallback to "unknown" again (identical). Step 5: dl==output? No —
    //   dl=randombytes, output=unknown. So reason stays LOW_CONFIDENCE per the Python algorithm.
    //
    // Key regression: the output MUST be "unknown", NEVER "randombytes". The overwrite-map is
    // consulted, not skipped. A reversed-order implementation would either leave the original
    // randombytes or fall back to TXT/UNKNOWN without consulting the overwrite map. Either
    // deviation is a parity break.
    assertThat(result.outputLabel().label()).isEqualTo("unknown");
    assertThat(result.dlLabel().label()).isEqualTo("randombytes");
    // reason: overwrite applied, then low-confidence path recomputed from "unknown" — stays
    // LOW_CONFIDENCE because dl != output. The Python step-5 check only resets to NONE when
    // output equals dl; here it doesn't.
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.LOW_CONFIDENCE);
  }

  @Test
  void overwrite_map_above_threshold_keeps_OVERWRITE_MAP_reason() {
    // Score above threshold (0.9) so Step 4 never fires. Overwrite-map applies; reason stays
    // OVERWRITE_MAP per the Python algorithm.
    InferenceResult raw = new InferenceResult(new float[] {0.9f}, "randombytes", 0.9);
    ResolvedLabels result = LabelResolver.resolve(
      raw, CFG, PredictionMode.HIGH_CONFIDENCE, REGISTRY);

    assertThat(result.outputLabel().label()).isEqualTo("unknown");
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.OVERWRITE_MAP);
  }

  @Test
  void best_guess_bypasses_threshold() {
    // In BEST_GUESS mode the effective threshold is 0.0 — low score still returns the dl label.
    InferenceResult raw = new InferenceResult(new float[] {0.1f}, "html", 0.1);
    ResolvedLabels result = LabelResolver.resolve(
      raw, CFG, PredictionMode.BEST_GUESS, REGISTRY);

    assertThat(result.outputLabel().label()).isEqualTo("html");
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.NONE);
  }

  @Test
  void high_confidence_uses_per_type_threshold() {
    // markdown threshold = 0.75; score 0.8 ≥ 0.75 → no fallback.
    InferenceResult raw = new InferenceResult(new float[] {0.8f}, "markdown", 0.8);
    ResolvedLabels result = LabelResolver.resolve(
      raw, CFG, PredictionMode.HIGH_CONFIDENCE, REGISTRY);

    assertThat(result.outputLabel().label()).isEqualTo("markdown");
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.NONE);
  }

  @Test
  void medium_confidence_ignores_per_type_threshold() {
    // MEDIUM_CONFIDENCE uses the global scalar (0.5), NOT the per-type 0.75 for markdown.
    // markdown score 0.6 would fail HIGH_CONFIDENCE (below 0.75) but passes MEDIUM (≥ 0.5).
    InferenceResult raw = new InferenceResult(new float[] {0.6f}, "markdown", 0.6);
    ResolvedLabels result = LabelResolver.resolve(
      raw, CFG, PredictionMode.MEDIUM_CONFIDENCE, REGISTRY);

    assertThat(result.outputLabel().label()).isEqualTo("markdown");
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.NONE);
  }
}
