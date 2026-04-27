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

import java.util.Objects;

/**
 * One prediction — either the raw deep-learning output ({@code dl}) or the post-processed output
 * ({@code output}) in a {@link MagikaResult}.
 *
 * <p>The {@code type} accessor returns a {@link ContentTypeLabel} value (the upstream label string
 * paired with its {@link dev.jcputney.magika.config.ContentTypeInfo} metadata row). Use
 * {@code .type().label()} for the bare upstream string ("png", "txt", "unknown", ...). Diverges
 * from upstream Python {@code MagikaPrediction.ct_label} naming for readability — the wrapper type
 * carries metadata Python returns separately.
 *
 * @param type            the resolved content-type (never null; sentinels {@code UNDEFINED} /
 *                        {@code EMPTY} / {@code UNKNOWN} / {@code TXT} cover edge cases)
 * @param score           the prediction score in {@code [0.0, 1.0]}
 * @param overwriteReason why {@code output.type} differs from {@code dl.type} ({@code NONE} if
 *                        equal)
 */
public record MagikaPrediction(
                               ContentTypeLabel type, double score, OverwriteReason overwriteReason) {

  public MagikaPrediction {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(overwriteReason, "overwriteReason");
  }
}
