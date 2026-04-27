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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable lookup from content-type label string → {@link ContentTypeInfo} row (CFG-02).
 * {@link #get(String)} returns {@link ContentTypeInfo#UNDEFINED} for unknown labels — never
 * {@code null}, per the CLAUDE.md "never null, never Optional" rule.
 *
 * @param byLabel immutable label → info map
 */
public record ContentTypeRegistry(Map<String, ContentTypeInfo> byLabel) {

  public ContentTypeRegistry {
    Objects.requireNonNull(byLabel, "byLabel");
    byLabel = Map.copyOf(byLabel);
  }

  /** Build a registry from the list shape Jackson deserializes from JSON-array files. */
  public static ContentTypeRegistry fromList(List<ContentTypeInfo> entries) {
    Objects.requireNonNull(entries, "entries");
    Map<String, ContentTypeInfo> map = new LinkedHashMap<>(entries.size() * 2);
    for (ContentTypeInfo entry : entries) {
      map.put(entry.label(), entry);
    }
    return new ContentTypeRegistry(map);
  }

  /** Look up a content-type by label; returns {@link ContentTypeInfo#UNDEFINED} if not found. */
  public ContentTypeInfo get(String label) {
    return byLabel.getOrDefault(label, ContentTypeInfo.UNDEFINED);
  }
}
