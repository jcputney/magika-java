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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable allowlist used by {@link Magika#verifyPath}, {@link Magika#verifyBytes}, and
 * {@link Magika#verifyStream}.
 *
 * <p>Constants are group-derived from Magika's bundled content-type metadata. Custom allowlists can
 * combine exact MIME types and exact Magika groups via {@link #ofMimeTypes}, {@link #ofGroups},
 * and {@link #anyOf}.
 *
 * @param mimeTypes exact MIME types accepted by this allowlist
 * @param groups    exact Magika groups accepted by this allowlist
 */
public record ExpectedContentTypes(Set<String> mimeTypes, Set<String> groups) {

  public static final ExpectedContentTypes IMAGE = ofGroups("image");
  public static final ExpectedContentTypes VIDEO = ofGroups("video");
  public static final ExpectedContentTypes AUDIO = ofGroups("audio");
  public static final ExpectedContentTypes DOCUMENT = ofGroups("document");
  public static final ExpectedContentTypes ARCHIVE = ofGroups("archive");
  public static final ExpectedContentTypes EXECUTABLE = ofGroups("executable");
  public static final ExpectedContentTypes CODE = ofGroups("code");
  public static final ExpectedContentTypes TEXT = ofGroups("text");
  public static final ExpectedContentTypes FONT = ofGroups("font");
  public static final ExpectedContentTypes APPLICATION = ofGroups("application");

  public ExpectedContentTypes {
    mimeTypes = normalizeAll(Objects.requireNonNull(mimeTypes, "mimeTypes"), "mimeTypes");
    groups = normalizeAll(Objects.requireNonNull(groups, "groups"), "groups");
  }

  /** Builds an allowlist from exact MIME type strings. */
  public static ExpectedContentTypes ofMimeTypes(String... mimeTypes) {
    Objects.requireNonNull(mimeTypes, "mimeTypes");
    return new ExpectedContentTypes(normalizeAll(Arrays.asList(mimeTypes), "mimeTypes"), Set.of());
  }

  /** Builds an allowlist from exact Magika group strings. */
  public static ExpectedContentTypes ofGroups(String... groups) {
    Objects.requireNonNull(groups, "groups");
    return new ExpectedContentTypes(Set.of(), normalizeAll(Arrays.asList(groups), "groups"));
  }

  /** Returns the union of multiple allowlists. */
  public static ExpectedContentTypes anyOf(ExpectedContentTypes... expected) {
    Objects.requireNonNull(expected, "expected");
    LinkedHashSet<String> mimeTypes = new LinkedHashSet<>();
    LinkedHashSet<String> groups = new LinkedHashSet<>();
    for (int i = 0; i < expected.length; i++) {
      ExpectedContentTypes item = Objects.requireNonNull(expected[i], "expected[" + i + "]");
      mimeTypes.addAll(item.mimeTypes());
      groups.addAll(item.groups());
    }
    return new ExpectedContentTypes(mimeTypes, groups);
  }

  /** True when the detected MIME type or group is accepted by this allowlist. */
  public boolean matches(DetectedContentType detected) {
    Objects.requireNonNull(detected, "detected");
    return matchesMime(detected) || matchesGroup(detected);
  }

  /** True when the detected MIME type is explicitly accepted by this allowlist. */
  public boolean matchesMime(DetectedContentType detected) {
    Objects.requireNonNull(detected, "detected");
    return detected.mimeType() != null && mimeTypes.contains(normalize(detected.mimeType(), "mimeType"));
  }

  /** True when the detected Magika group is explicitly accepted by this allowlist. */
  public boolean matchesGroup(DetectedContentType detected) {
    Objects.requireNonNull(detected, "detected");
    return detected.group() != null && groups.contains(normalize(detected.group(), "group"));
  }

  private static Set<String> normalizeAll(Collection<String> values, String name) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    int i = 0;
    for (String value : values) {
      out.add(normalize(value, name + "[" + i + "]"));
      i++;
    }
    return Collections.unmodifiableSet(out);
  }

  private static String normalize(String value, String name) {
    Objects.requireNonNull(value, name);
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return normalized;
  }
}
