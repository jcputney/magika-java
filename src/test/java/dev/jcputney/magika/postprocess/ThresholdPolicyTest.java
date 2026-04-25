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

import dev.jcputney.magika.config.ThresholdConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Isolated unit tests for {@link ThresholdPolicy} (DEBT-03 / POST-02). Mirrors upstream Python
 * {@code magika.py:593-615} threshold formula for each {@link PredictionMode}. See
 * {@code docs/algorithm-notes.md} §"Prediction-mode threshold semantics" → §"ThresholdPolicy
 * class extraction".
 */
@Tag("unit")
class ThresholdPolicyTest {

  private static final ThresholdConfig CFG = new ThresholdConfig(
    1024, 0, 1024, 4096, 256, 8,
    0.5,
    false,
    List.of("markdown", "txt"),
    Map.of("markdown", 0.75),
    Map.of(),
    3);

  @Test
  void best_guess_returns_zero() {
    double t = ThresholdPolicy.resolve(PredictionMode.BEST_GUESS, "markdown", CFG);
    assertThat(t).isEqualTo(0.0);
  }

  @Test
  void medium_confidence_uses_scalar() {
    double t = ThresholdPolicy.resolve(PredictionMode.MEDIUM_CONFIDENCE, "any-label", CFG);
    assertThat(t).isEqualTo(0.5);
  }

  @Test
  void medium_confidence_ignores_per_type_map() {
    // Pitfall regression: even though "markdown" is in the per-type map at 0.75,
    // MEDIUM_CONFIDENCE returns the global 0.5 scalar. Cite magika.py:610.
    double t = ThresholdPolicy.resolve(PredictionMode.MEDIUM_CONFIDENCE, "markdown", CFG);
    assertThat(t).isEqualTo(0.5);
  }

  @Test
  void high_confidence_uses_per_type_threshold_when_present() {
    double t = ThresholdPolicy.resolve(PredictionMode.HIGH_CONFIDENCE, "markdown", CFG);
    assertThat(t).isEqualTo(0.75);
  }

  @Test
  void high_confidence_falls_back_to_scalar_for_unmapped_label() {
    double t = ThresholdPolicy.resolve(PredictionMode.HIGH_CONFIDENCE, "txt", CFG);
    assertThat(t).isEqualTo(0.5);
  }
}
