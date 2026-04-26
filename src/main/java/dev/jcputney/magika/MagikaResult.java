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
 * Result of a Magika identify call — matches upstream Python shape (API-05 + REF-01 / A-05).
 * Field names are {@code dl} / {@code output} / {@code score} / {@code status} exactly as
 * Python's {@code MagikaResult}; do not camel-case or rename.
 *
 * @param dl     raw deep-learning prediction (never null; {@code UNDEFINED} sentinel when model
 *               skipped via the small-file branch)
 * @param output post-processed prediction — may differ from {@code dl} via overwrite-map or
 *               threshold fallback
 * @param score  the {@code output} score
 * @param status the result status (REF-01 / A-01) — always {@link Status#OK} on single-call
 *               success; non-OK values populated only by batch results per A-02
 */
public record MagikaResult(MagikaPrediction dl, MagikaPrediction output, double score, Status status) {

  public MagikaResult {
    Objects.requireNonNull(dl, "dl");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(status, "status");
  }
}
