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

import dev.jcputney.magika.postprocess.ContentTypeLabel;
import dev.jcputney.magika.postprocess.OverwriteReason;
import java.util.Objects;

/**
 * One prediction — either the raw deep-learning output ({@code dl}) or the post-processed output
 * ({@code output}) in a {@link MagikaResult}. Field names follow upstream Python
 * {@code MagikaPrediction} verbatim (API-06).
 *
 * @param label           the content-type label (never null; sentinels {@code UNDEFINED} /
 *                        {@code EMPTY} / {@code UNKNOWN} / {@code TXT} cover edge cases)
 * @param score           the prediction score in {@code [0.0, 1.0]}
 * @param overwriteReason why {@code output.label} differs from {@code dl.label} ({@code NONE} if
 *                        equal)
 */
public record MagikaPrediction(
                               ContentTypeLabel label, double score, OverwriteReason overwriteReason) {

  public MagikaPrediction {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(overwriteReason, "overwriteReason");
  }
}
