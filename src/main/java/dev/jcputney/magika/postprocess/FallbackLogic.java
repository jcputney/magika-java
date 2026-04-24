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

import dev.jcputney.magika.config.ContentTypeRegistry;
import dev.jcputney.magika.config.ThresholdConfig;

/**
 * Pre-model small-file / empty-file branch resolver (POST-03, POST-04). Skips
 * {@code OrtSession.run()} for files too small to be meaningful model input. Cite:
 * {@code docs/algorithm-notes.md} §"Small-file branches" (4-row branch table).
 *
 * <p>Return semantics:
 *
 * <ul>
 * <li>{@code N == 0} → {@code (UNDEFINED, EMPTY, 1.0, NONE)}
 * <li>{@code 0 < N < minFileSizeForDl} → decode strict UTF-8; {@code TXT} on success,
 * {@code UNKNOWN} on failure; dl remains {@code UNDEFINED}; score 1.0; reason NONE
 * <li>{@code N >= minFileSizeForDl} → returns {@code null}; caller invokes the model
 * </ul>
 *
 * <p>Per CLAUDE.md: "Small-file branches skip {@code OrtSession.run()}. Return
 * {@link ContentTypeLabel#UNDEFINED} sentinel — never {@code null}, never
 * {@code Optional}." The only null return here signals "run the model"; it is not a
 * missing-value signal.
 */
public final class FallbackLogic {

  private FallbackLogic() {
    // utility class
  }

  /**
   * Decide whether the content triggers a small-file branch. Returns a {@link ResolvedLabels}
   * with the small-file decision; returns {@code null} if the caller should invoke the model.
   */
  public static ResolvedLabels smallFileBranch(
    byte[] content, int length, ThresholdConfig cfg, ContentTypeRegistry registry) {
    if (length == 0) {
      return new ResolvedLabels(
        ContentTypeLabel.UNDEFINED,
        ContentTypeLabel.EMPTY,
        1.0,
        OverwriteReason.NONE);
    }
    if (length < cfg.minFileSizeForDl()) {
      ContentTypeLabel output = Utf8Validator.isValid(content, 0, length) ? ContentTypeLabel.TXT :
        ContentTypeLabel.UNKNOWN;
      return new ResolvedLabels(
        ContentTypeLabel.UNDEFINED, output, 1.0, OverwriteReason.NONE);
    }
    // N >= minFileSizeForDl — caller runs the model.
    return null;
  }
}
