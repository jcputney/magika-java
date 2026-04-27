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
import java.util.List;
import java.util.Objects;

/**
 * MIME-focused view of a {@link MagikaResult}.
 *
 * <p>This record exposes the metadata consumers usually need when replacing
 * {@code Tika.detect(...)} style APIs while preserving Magika-specific details such as label,
 * group, score, and status.
 *
 * @param label       upstream Magika label, for example {@code "png"} or {@code "pdf"}
 * @param mimeType    MIME type from Magika's bundled registry; may be null for registry rows that
 *                    do not define one
 * @param group       Magika content group, for example {@code "image"}; may be null
 * @param description human-readable registry description; may be null
 * @param extensions  known extensions for this label
 * @param isText      whether Magika marks the label as text
 * @param score       output confidence score
 * @param status      result status
 */
public record DetectedContentType(
                                  String label,
                                  String mimeType,
                                  String group,
                                  String description,
                                  List<String> extensions,
                                  boolean isText,
                                  double score,
                                  Status status) {

  public DetectedContentType {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(extensions, "extensions");
    Objects.requireNonNull(status, "status");
    extensions = List.copyOf(extensions);
  }

  /** Builds a MIME-focused view from an existing Magika result. */
  public static DetectedContentType from(MagikaResult result) {
    Objects.requireNonNull(result, "result");
    ContentTypeLabel type = result.output().type();
    ContentTypeInfo info = type.info();
    return new DetectedContentType(
      type.label(),
      info.mimeType(),
      info.group(),
      info.description(),
      info.extensions(),
      info.isText(),
      result.score(),
      result.status());
  }

  /** True for Magika's sentinel labels that are not useful concrete content types. */
  public boolean isUnknownLike() {
    return "unknown".equals(label) || "undefined".equals(label) || "empty".equals(label);
  }
}
