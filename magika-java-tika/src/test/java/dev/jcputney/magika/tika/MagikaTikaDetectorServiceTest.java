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

package dev.jcputney.magika.tika;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ServiceLoader;
import org.apache.tika.detect.Detector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class MagikaTikaDetectorServiceTest {

  @Test
  void detector_is_registered_for_tika_service_loading() {
    boolean found = ServiceLoader.load(Detector.class)
      .stream()
      .anyMatch(provider -> provider.type().equals(MagikaTikaDetector.class));

    assertThat(found).isTrue();
  }
}
