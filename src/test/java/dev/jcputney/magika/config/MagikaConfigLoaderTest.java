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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jcputney.magika.ModelLoadException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class MagikaConfigLoaderTest {

  @Test
  void loads_minimal_config() {
    ThresholdConfig cfg = MagikaConfigLoader.loadThresholdConfig(
      "/dev/jcputney/magika/config-minimal.json");

    assertThat(cfg.begSize()).isEqualTo(1024);
    assertThat(cfg.midSize()).isZero();
    assertThat(cfg.endSize()).isEqualTo(1024);
    assertThat(cfg.blockSize()).isEqualTo(4096);
    assertThat(cfg.paddingToken()).isEqualTo(256);
    assertThat(cfg.minFileSizeForDl()).isEqualTo(8);
    assertThat(cfg.mediumConfidenceThreshold()).isEqualTo(0.5);
    assertThat(cfg.useInputsAtOffsets()).isFalse();
    assertThat(cfg.versionMajor()).isEqualTo(3);
    assertThat(cfg.targetLabelsSpace()).contains("html", "randombytes", "randomtxt", "markdown");
    assertThat(cfg.thresholds()).containsEntry("markdown", 0.75);
    assertThat(cfg.overwriteMap()).containsEntry("randombytes", "unknown");
    assertThat(cfg.overwriteMap()).containsEntry("randomtxt", "txt");
  }

  @Test
  void tolerates_unknown_properties() {
    // CFG-05: FAIL_ON_UNKNOWN_PROPERTIES=false — upstream can add fields without breaking us.
    ThresholdConfig cfg = MagikaConfigLoader.loadThresholdConfig(
      "/dev/jcputney/magika/config-extra-field.json");

    assertThat(cfg.begSize()).isEqualTo(1024);
    assertThat(cfg.versionMajor()).isEqualTo(3);
  }

  @Test
  void rejects_missing_required_primitive() {
    // CFG-05: FAIL_ON_NULL_FOR_PRIMITIVES=true — missing min_file_size_for_dl must fail loudly.
    assertThatThrownBy(() -> MagikaConfigLoader.loadThresholdConfig(
      "/dev/jcputney/magika/config-missing-primitive.json"))
      .isInstanceOf(ModelLoadException.class)
      .hasMessageContaining("min_file_size_for_dl");
  }

  @Test
  void missing_resource_throws_model_load() {
    assertThatThrownBy(() -> MagikaConfigLoader.loadThresholdConfig(
      "/dev/jcputney/magika/does-not-exist.json"))
      .isInstanceOf(ModelLoadException.class)
      .hasMessageContaining("does-not-exist.json");
  }

  @Test
  void loads_content_types_registry() {
    ContentTypeRegistry registry = MagikaConfigLoader.loadRegistry(
      "/dev/jcputney/magika/content-types-minimal.json");

    assertThat(registry.get("html").isText()).isTrue();
    assertThat(registry.get("html").mimeType()).isEqualTo("text/html");
    assertThat(registry.get("zip").isText()).isFalse();
    // Missing label returns UNDEFINED sentinel (never null).
    assertThat(registry.get("nope")).isSameAs(ContentTypeInfo.UNDEFINED);
  }
}
