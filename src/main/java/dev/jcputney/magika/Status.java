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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result status — verbatim 4-value mirror of upstream Python's Status enum (REF-01 / A-01).
 *
 * <p>Wire format mirrors upstream Python's {@code LowerCaseStrEnum}: lowercase + snake_case.
 * Each constant carries a {@link JsonProperty} annotation so Jackson deserializes the upstream
 * sidecar value (e.g. {@code "ok"}) directly to the matching Java constant.
 *
 * <p>Single-call vs batch semantics (A-02): Single-call paths ({@code identifyPath},
 * {@code identifyBytes}, {@code identifyStream}) ALWAYS produce {@link #OK} on success and
 * continue to throw their existing {@code InvalidInputException} / {@code InferenceException} /
 * {@code ModelLoadException} on failure (the v0.1 lifecycle contract is preserved bit-for-bit).
 * Non-OK values appear ONLY on results returned by the batch entry point {@code identifyPaths}
 * per the per-file failure mapping documented in that method's Javadoc.
 */
public enum Status {

  /** Successful detection — populated on every single-call success and on batch successes. */
  @JsonProperty("ok") OK,

  /** Batch-only — caller passed a path that does not exist (NoSuchFileException). */
  @JsonProperty("file_not_found_error") FILE_NOT_FOUND_ERROR,

  /** Batch-only — caller passed a path that is unreadable (AccessDeniedException). */
  @JsonProperty("permission_error") PERMISSION_ERROR,

  /** Batch-only — any other failure (IOException / InvalidInputException / InferenceException). */
  @JsonProperty("unknown") UNKNOWN
}
