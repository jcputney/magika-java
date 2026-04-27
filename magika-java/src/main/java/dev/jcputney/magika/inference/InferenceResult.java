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

package dev.jcputney.magika.inference;

import java.util.Objects;

/**
 * Raw output of one {@link InferenceEngine#run} call. Components mirror the shape upstream
 * Python's {@code magika.py} consumes post-softmax: the full probability vector, the argmax
 * label string, and the score at that argmax.
 *
 * @param probabilities       the full softmax probability vector across
 *                            {@code target_labels_space} (non-null)
 * @param topContentTypeLabel argmax label as a {@code ContentTypeLabel} string (non-null)
 * @param topScore            probability at {@code argmax} (i.e. {@code probabilities[argmaxIndex]})
 */
public record InferenceResult(
                              float[] probabilities, String topContentTypeLabel, double topScore) {

  public InferenceResult {
    Objects.requireNonNull(probabilities, "probabilities");
    Objects.requireNonNull(topContentTypeLabel, "topContentTypeLabel");
  }
}
