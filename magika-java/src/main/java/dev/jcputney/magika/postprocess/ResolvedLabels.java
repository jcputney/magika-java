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

import dev.jcputney.magika.ContentTypeLabel;
import dev.jcputney.magika.OverwriteReason;
import java.util.Objects;

/**
 * Output of {@link LabelResolver#resolve} / {@link FallbackLogic#smallFileBranch} (API-07,
 * POST-01..06). Carries both the raw deep-learning label and the post-threshold /
 * post-overwrite-map output label plus the {@link OverwriteReason} signal that explains any
 * divergence.
 *
 * @param dlLabel         the raw top-1 content-type from the model (or {@code UNDEFINED} on
 *                        small-file branches)
 * @param outputLabel     the final resolved content-type after overwrite-map + threshold logic
 * @param score           the {@code topScore} from the model (or 1.0 on small-file branches)
 * @param overwriteReason why {@code outputLabel} differs from {@code dlLabel}, or {@code NONE}
 */
public record ResolvedLabels(
                             ContentTypeLabel dlLabel,
                             ContentTypeLabel outputLabel,
                             double score,
                             OverwriteReason overwriteReason) {

  public ResolvedLabels {
    Objects.requireNonNull(dlLabel, "dlLabel");
    Objects.requireNonNull(outputLabel, "outputLabel");
    Objects.requireNonNull(overwriteReason, "overwriteReason");
  }
}
