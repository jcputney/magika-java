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

import dev.jcputney.magika.ContentTypeLabel;
import dev.jcputney.magika.OverwriteReason;
import dev.jcputney.magika.config.ContentTypeRegistry;
import dev.jcputney.magika.config.ThresholdConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class FallbackLogicTest {

  private static final ThresholdConfig CFG = new ThresholdConfig(
    1024, 0, 1024, 4096, 256, 8,
    0.5, false,
    List.of("unknown", "empty", "undefined", "txt", "html"),
    Map.of(),
    Map.of(),
    3);

  private static final ContentTypeRegistry REGISTRY = ContentTypeRegistry.fromList(List.of());

  @Test
  void empty_file_returns_EMPTY_sentinel() {
    ResolvedLabels result = FallbackLogic.smallFileBranch(new byte[0], 0, CFG, REGISTRY);

    assertThat(result).isNotNull();
    assertThat(result.dlLabel()).isSameAs(ContentTypeLabel.UNDEFINED);
    assertThat(result.outputLabel()).isSameAs(ContentTypeLabel.EMPTY);
    assertThat(result.score()).isEqualTo(1.0);
    assertThat(result.overwriteReason()).isEqualTo(OverwriteReason.NONE);
  }

  @Test
  void tiny_valid_utf8_returns_TXT() {
    byte[] content = new byte[] {'a', 'b', 'c'};
    ResolvedLabels result = FallbackLogic.smallFileBranch(content, 3, CFG, REGISTRY);

    assertThat(result).isNotNull();
    assertThat(result.dlLabel()).isSameAs(ContentTypeLabel.UNDEFINED);
    assertThat(result.outputLabel()).isSameAs(ContentTypeLabel.TXT);
    assertThat(result.score()).isEqualTo(1.0);
  }

  @Test
  void tiny_invalid_utf8_returns_UNKNOWN() {
    byte[] content = new byte[] {(byte) 0xFF};
    ResolvedLabels result = FallbackLogic.smallFileBranch(content, 1, CFG, REGISTRY);

    assertThat(result).isNotNull();
    assertThat(result.dlLabel()).isSameAs(ContentTypeLabel.UNDEFINED);
    assertThat(result.outputLabel()).isSameAs(ContentTypeLabel.UNKNOWN);
  }

  @Test
  void above_min_size_returns_null_to_signal_run_model() {
    byte[] content = new byte[100];
    ResolvedLabels result = FallbackLogic.smallFileBranch(content, 100, CFG, REGISTRY);

    assertThat(result).isNull();
  }

  @Test
  void exactly_min_size_returns_null_to_signal_run_model() {
    // Boundary: minFileSizeForDl=8 is NOT a small-file (length < min_file_size_for_dl is the
    // upstream condition).
    byte[] content = new byte[8];
    ResolvedLabels result = FallbackLogic.smallFileBranch(content, 8, CFG, REGISTRY);

    assertThat(result).isNull();
  }
}
