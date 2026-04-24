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

import dev.jcputney.magika.config.ContentTypeInfo;
import dev.jcputney.magika.config.ContentTypeRegistry;
import dev.jcputney.magika.config.ThresholdConfig;
import dev.jcputney.magika.inference.InferenceResult;

/**
 * Resolves the raw model output to a user-facing {@link ResolvedLabels} (POST-01, POST-02,
 * POST-05). Implements verbatim the 5-step pseudocode from {@code docs/algorithm-notes.md}
 * §"Overwrite-map ordering" (cites Python {@code magika.py:578-634}).
 *
 * <p><strong>Overwrite-map is applied BEFORE the threshold check, not after.</strong> Reversing
 * the ordering silently breaks parity on the two overwrite-map entries in {@code standard_v3_3}
 * ({@code randombytes}, {@code randomtxt}). See PITFALLS.md §Pitfall 5. A dedicated regression
 * test ({@code LabelResolverTest.overwrite_map_applied_BEFORE_threshold}) locks the contract.
 *
 * <p>Per D-11, this class does NOT emit log events; the DEBUG-on-overwrite-hit log lives in the
 * {@code Magika} facade (Plan 5) to keep the pure resolver side-effect-free.
 */
public final class LabelResolver {

  private LabelResolver() {
    // utility class
  }

  /**
   * Resolve the raw {@link InferenceResult} to a {@link ResolvedLabels} per the 5-step Python
   * algorithm (overwrite-map → threshold → TXT/UNKNOWN fallback → reason reset-if-identical).
   */
  public static ResolvedLabels resolve(
    InferenceResult raw,
    ThresholdConfig cfg,
    PredictionMode mode,
    ContentTypeRegistry registry) {
    // Step 1: start with dl → output, reason NONE.
    String dlLabelString = raw.topContentTypeLabel();
    double score = raw.topScore();
    String outputLabelString = dlLabelString;
    OverwriteReason reason = OverwriteReason.NONE;

    // Step 2: overwrite-map lookup (applied BEFORE the threshold check — POST-05 / Pitfall 5).
    if (cfg.overwriteMap().containsKey(dlLabelString)) {
      outputLabelString = cfg.overwriteMap().get(dlLabelString);
      reason = OverwriteReason.OVERWRITE_MAP;
    }

    // Step 3: determine effective threshold per prediction mode
    // (§"Prediction-mode threshold semantics").
    double threshold = effectiveThreshold(mode, dlLabelString, cfg);

    // Step 4: below-threshold → TXT/UNKNOWN fallback, reason LOW_CONFIDENCE.
    if (score < threshold) {
      reason = OverwriteReason.LOW_CONFIDENCE;
      ContentTypeInfo info = registry.get(outputLabelString);
      outputLabelString = info.isText() ? "txt" : "unknown";
    }

    // Step 5: if output matches dl, reset reason to NONE (never leak a spurious signal).
    if (outputLabelString.equals(dlLabelString)) {
      reason = OverwriteReason.NONE;
    }

    // Materialize ContentTypeLabel values.
    ContentTypeLabel dl = new ContentTypeLabel(dlLabelString, registry.get(dlLabelString));
    ContentTypeLabel out = resolveLabel(outputLabelString, registry);
    return new ResolvedLabels(dl, out, score, reason);
  }

  private static double effectiveThreshold(
    PredictionMode mode, String dlLabelString, ThresholdConfig cfg) {
    return switch (mode) {
      case BEST_GUESS -> 0.0;
      case MEDIUM_CONFIDENCE -> cfg.mediumConfidenceThreshold();
      case HIGH_CONFIDENCE -> cfg.thresholds()
        .getOrDefault(dlLabelString, cfg.mediumConfidenceThreshold());
    };
  }

  private static ContentTypeLabel resolveLabel(String label, ContentTypeRegistry registry) {
    return switch (label) {
      case "txt" -> ContentTypeLabel.TXT;
      case "unknown" -> ContentTypeLabel.UNKNOWN;
      case "empty" -> ContentTypeLabel.EMPTY;
      case "undefined" -> ContentTypeLabel.UNDEFINED;
      default -> new ContentTypeLabel(label, registry.get(label));
    };
  }
}
