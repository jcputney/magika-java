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

/**
 * Why {@code output.label} differs from {@code dl.label} (API-06, ALG-08). Exactly three values,
 * matching upstream {@code python/src/magika/types/overwrite_reason.py}.
 *
 * <p>Lives in the exported public API package so module-path consumers can read the
 * {@link MagikaPrediction#overwriteReason()} accessor.
 *
 * <ul>
 * <li>{@link #NONE} — {@code output.label == dl.label} (no overwrite applied).
 * <li>{@link #LOW_CONFIDENCE} — score below threshold; fell back to TXT/UNKNOWN.
 * <li>{@link #OVERWRITE_MAP} — {@code dl.label} was rewritten via {@code overwrite_map} lookup.
 * </ul>
 */
public enum OverwriteReason {
  NONE,
  LOW_CONFIDENCE,
  OVERWRITE_MAP
}
