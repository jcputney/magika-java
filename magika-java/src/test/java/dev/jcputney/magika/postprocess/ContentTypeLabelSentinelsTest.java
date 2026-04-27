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


import static org.assertj.core.api.Assertions.assertThat;

import dev.jcputney.magika.config.ContentTypeInfo;
import dev.jcputney.magika.config.ContentTypeRegistry;
import dev.jcputney.magika.config.MagikaConfigLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * DEBT-01 invariant: the static-final sentinel {@link ContentTypeInfo} constants in
 * {@code config.ContentTypeInfo} (TXT, UNKNOWN, EMPTY) agree with the bundled
 * {@code content_types_kb.min.json} rows. UNDEFINED is the registry-miss return value, never a
 * registry-hit. If upstream ever shifts a sentinel row (mime_type, group, description, extensions,
 * is_text), this test surfaces the drift at build time, not at first identify call.
 */
@Tag("unit")
class ContentTypeLabelSentinelsTest {

  @Test
  void txt_sentinel_matches_bundled_registry_entry() {
    ContentTypeRegistry registry = MagikaConfigLoader.loadBundledRegistry();
    ContentTypeInfo upstreamTxt = registry.get("txt");
    assertThat(upstreamTxt).isNotSameAs(ContentTypeInfo.UNDEFINED);
    // DEBT-01 invariant: the hand-written TXT sentinel agrees with the bundled
    // content_types_kb.min.json row. If upstream ever shifts the txt row
    // (mime_type, group, description, extensions, is_text), this test surfaces
    // the latent doc-rot at build time, not at first identify call.
    assertThat(ContentTypeInfo.TXT).isEqualTo(upstreamTxt);
  }

  @Test
  void unknown_sentinel_matches_bundled_registry_entry() {
    ContentTypeRegistry registry = MagikaConfigLoader.loadBundledRegistry();
    assertThat(ContentTypeInfo.UNKNOWN).isEqualTo(registry.get("unknown"));
  }

  @Test
  void empty_sentinel_matches_bundled_registry_entry() {
    ContentTypeRegistry registry = MagikaConfigLoader.loadBundledRegistry();
    assertThat(ContentTypeInfo.EMPTY).isEqualTo(registry.get("empty"));
  }

  @Test
  void undefined_sentinel_returned_for_unknown_label() {
    // UNDEFINED is the registry-miss return value, never a registry-hit. Re-asserted here as the
    // dual of the three positive sentinels.
    ContentTypeRegistry registry = MagikaConfigLoader.loadBundledRegistry();
    assertThat(registry.get("definitely-not-a-label-xyz")).isSameAs(ContentTypeInfo.UNDEFINED);
  }
}
