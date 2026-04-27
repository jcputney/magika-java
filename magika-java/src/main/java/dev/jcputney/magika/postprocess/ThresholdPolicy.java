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

import dev.jcputney.magika.PredictionMode;
import dev.jcputney.magika.config.ThresholdConfig;

/**
 * Resolves the effective threshold for a label given a {@link PredictionMode} (POST-02 / DEBT-03).
 *
 * <p>Cite: {@code docs/algorithm-notes.md} §"Prediction-mode threshold semantics" → §"ThresholdPolicy
 * class extraction". Body is byte-identical to upstream Python {@code magika.py:593-615}; arms in
 * the same order; same {@code Map.getOrDefault} fallback to {@code mediumConfidenceThreshold}.
 *
 * <p>Per package-isolation rules: this class lives in {@code postprocess}, depends only on
 * {@link ThresholdConfig} (config — read-only consumer) and {@link PredictionMode} (public API
 * enum). Zero ONNX, zero Jackson.
 */
public final class ThresholdPolicy {

  private ThresholdPolicy() {
    // utility class
  }

  /**
   * Resolve the effective score threshold below which {@code LabelResolver} falls back to
   * TXT/UNKNOWN. Mirrors upstream Python {@code magika.py:593-615} arm-for-arm.
   *
   * @param mode    the requested prediction mode
   * @param dlLabel the model's top-label string (consulted only by {@code HIGH_CONFIDENCE})
   * @param cfg     the loaded threshold configuration
   * @return the threshold value
   */
  public static double resolve(PredictionMode mode, String dlLabel, ThresholdConfig cfg) {
    return switch (mode) {
      case BEST_GUESS -> 0.0;
      case MEDIUM_CONFIDENCE -> cfg.mediumConfidenceThreshold();
      case HIGH_CONFIDENCE -> cfg.thresholds()
        .getOrDefault(dlLabel, cfg.mediumConfidenceThreshold());
    };
  }
}
