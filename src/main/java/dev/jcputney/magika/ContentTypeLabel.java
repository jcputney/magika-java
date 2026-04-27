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

import dev.jcputney.magika.config.ContentTypeInfo;
import java.util.Objects;

/**
 * A resolved content-type — pairs the upstream {@code ContentTypeLabel} string with the
 * {@link ContentTypeInfo} row from {@code content_types_kb.min.json} (API-07).
 *
 * <p>Lives in the exported public API package so module-path consumers can chain
 * {@code result.output().type().label()} and dereference the {@link #label()} accessor without
 * needing access to a non-exported package. The {@link #info()} accessor returns
 * {@link ContentTypeInfo}, which currently lives in the internal {@code config} package — module-
 * path consumers can call {@code .info()} but cannot bind its return value to a typed variable.
 *
 * <p>Four sentinels cover the small-file, low-confidence, and unknown-prediction branches. Per
 * {@code docs/algorithm-notes.md} §"Small-file branches", these are values, NOT {@code null} /
 * {@code Optional}.
 *
 * @param label the upstream {@code ContentTypeLabel} string (non-null)
 * @param info  the corresponding {@link ContentTypeInfo} row (non-null)
 */
public record ContentTypeLabel(String label, ContentTypeInfo info) {

  public ContentTypeLabel {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(info, "info");
  }

  /** dl-label sentinel for pre-model small-file branches. */
  public static final ContentTypeLabel UNDEFINED =
    new ContentTypeLabel("undefined", ContentTypeInfo.UNDEFINED);

  /** Output sentinel for empty files (N == 0). */
  public static final ContentTypeLabel EMPTY =
    new ContentTypeLabel("empty", ContentTypeInfo.EMPTY);

  /** Output sentinel for files with no confident classification and no text-subtype fallback. */
  public static final ContentTypeLabel UNKNOWN =
    new ContentTypeLabel("unknown", ContentTypeInfo.UNKNOWN);

  /** Output sentinel for low-confidence / small-file paths that decode as valid UTF-8. */
  public static final ContentTypeLabel TXT = new ContentTypeLabel("txt", ContentTypeInfo.TXT);
}
