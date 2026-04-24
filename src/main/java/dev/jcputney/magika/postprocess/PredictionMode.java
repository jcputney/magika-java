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

/**
 * Threshold policy for {@code LabelResolver} (API-08). Exactly three values, matching upstream
 * Python {@code PredictionMode} in name and order.
 *
 * <p>Cite: {@code docs/algorithm-notes.md} §"Overwrite-map ordering" §"Prediction-mode default"
 * (Python {@code magika.py:60} sets {@link #HIGH_CONFIDENCE} as the constructor default).
 *
 * <ul>
 * <li>{@link #BEST_GUESS} — threshold = 0.0 (never falls back to TXT/UNKNOWN).
 * <li>{@link #MEDIUM_CONFIDENCE} — global {@code medium_confidence_threshold} scalar; per-type
 * {@code thresholds} map is ignored (see {@code magika.py:610}).
 * <li>{@link #HIGH_CONFIDENCE} — {@code thresholds.get(dl_label, medium_confidence_threshold)};
 * per-type override with scalar fallback.
 * </ul>
 */
public enum PredictionMode {
  BEST_GUESS,
  MEDIUM_CONFIDENCE,
  HIGH_CONFIDENCE;

  /** Upstream default ({@code magika.py:60}). */
  public static final PredictionMode DEFAULT = HIGH_CONFIDENCE;
}
