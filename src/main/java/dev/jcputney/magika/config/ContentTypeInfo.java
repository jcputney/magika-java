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

package dev.jcputney.magika.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * One row of {@code content_types_kb.min.json} (CFG-02). Jackson deserializes via the canonical
 * record constructor; {@code -parameters} compile flag + explicit {@link JsonProperty} annotations
 * are both load-bearing per PITFALLS.md §Pitfall 9.
 *
 * <p>Per {@code docs/algorithm-notes.md} §"content_types_kb.json schema", upstream (2026-04-24
 * fetch) carries 353 entries. {@code mimeType}, {@code group}, and {@code description} are scalar
 * {@code String} (nullable across upstream entries). {@code extensions} is always an array
 * (possibly empty). {@code is_text} is always a boolean.
 *
 * @param label       content-type label (outer map key; non-null)
 * @param group       coarse category (e.g. {@code "text"}, {@code "video"}); may be null
 * @param mimeType    IANA media type (e.g. {@code "text/plain"}); may be null
 * @param description human-readable description; may be null
 * @param extensions  filename extensions associated with this type (never null; possibly empty)
 * @param isText      true if {@code LabelResolver}'s low-confidence fallback should map to TXT
 */
public record ContentTypeInfo(
                              @JsonProperty("label")
                              String label,
                              @JsonProperty("group")
                              String group,
                              @JsonProperty("mime_type")
                              String mimeType,
                              @JsonProperty("description")
                              String description,
                              @JsonProperty("extensions")
                              List<String> extensions,
                              @JsonProperty("is_text")
                              boolean isText) {

  public ContentTypeInfo {
    Objects.requireNonNull(label, "label");
    extensions = extensions == null ? List.of() : List.copyOf(extensions);
  }

  /**
   * Sentinel used for "model predicted a label that isn't in the registry" and for the pre-model
   * {@code UNDEFINED} dl-label in small-file branches. Cite {@code docs/algorithm-notes.md}
   * §"Small-file branches".
   */
  public static final ContentTypeInfo UNDEFINED = new ContentTypeInfo(
    "undefined",
    "undefined",
    "application/octet-stream",
    "Undefined (no model inference)",
    List.of(),
    false);

  /**
   * Sentinel for empty-file branch (size 0). Cite {@code docs/algorithm-notes.md} §"Small-file
   * branches" (DEBT-01 / WR-04 — relocated from {@code postprocess.ContentTypeLabel}).
   */
  public static final ContentTypeInfo EMPTY = new ContentTypeInfo(
    "empty",
    "inode",
    "inode/x-empty",
    "Empty file (size 0)",
    List.of(),
    false);

  /**
   * Sentinel for unknown / non-text fallback. Cite {@code docs/algorithm-notes.md} §"Small-file
   * branches" (DEBT-01 / WR-04 — relocated from {@code postprocess.ContentTypeLabel}).
   */
  public static final ContentTypeInfo UNKNOWN = new ContentTypeInfo(
    "unknown",
    "unknown",
    "application/octet-stream",
    "Unknown binary",
    List.of(),
    false);

  /**
   * Sentinel for plain-text fallback (small-file UTF-8-decode branch + LOW_CONFIDENCE override).
   * Cite {@code docs/algorithm-notes.md} §"Small-file branches" and §"Overwrite-map ordering"
   * (DEBT-01 / WR-04 — relocated from {@code postprocess.ContentTypeLabel}).
   */
  public static final ContentTypeInfo TXT = new ContentTypeInfo(
    "txt",
    "text",
    "text/plain",
    "Plain text",
    List.of("txt"),
    true);
}
