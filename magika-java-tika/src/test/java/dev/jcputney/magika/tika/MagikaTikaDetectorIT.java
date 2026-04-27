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

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("parity")
class MagikaTikaDetectorIT {

  private static final Path CORE_FIXTURES = Path.of("../magika-java/src/test/resources/fixtures");

  @Test
  void detects_known_png_and_emits_metadata() throws Exception {
    byte[] bytes = Files.readAllBytes(CORE_FIXTURES.resolve("images/sample.png"));
    Metadata metadata = new Metadata();

    try (MagikaTikaDetector detector = MagikaTikaDetector.builder().build()) {
      MediaType mediaType = detector.detect(new ByteArrayInputStream(bytes), metadata);

      assertThat(mediaType).isEqualTo(MediaType.image("png"));
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_STATUS)).isEqualTo("ok");
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_LABEL)).isEqualTo("png");
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_MIME)).isEqualTo("image/png");
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_GROUP)).isEqualTo("image");
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_VERSION)).isEqualTo("v3_3");
    }
  }

  @Test
  void detect_resets_mark_supported_streams() throws Exception {
    byte[] bytes = Files.readAllBytes(CORE_FIXTURES.resolve("images/sample.png"));
    ByteArrayInputStream input = new ByteArrayInputStream(bytes);

    try (MagikaTikaDetector detector = MagikaTikaDetector.builder().build()) {
      detector.detect(input, new Metadata());
    }

    assertThat(input.read()).isEqualTo(Byte.toUnsignedInt(bytes[0]));
  }

  @Test
  void empty_detection_returns_null_by_default_but_still_emits_metadata() throws Exception {
    Metadata metadata = new Metadata();

    try (MagikaTikaDetector detector = MagikaTikaDetector.builder().build()) {
      MediaType mediaType = detector.detect(new ByteArrayInputStream(new byte[0]), metadata);

      assertThat(mediaType).isNull();
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_STATUS)).isEqualTo("ok");
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_LABEL)).isEqualTo("empty");
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_MIME)).isEqualTo("inode/x-empty");
    }
  }

  @Test
  void unknown_handling_can_return_octet_stream() throws Exception {
    try (MagikaTikaDetector detector = MagikaTikaDetector.builder()
      .unknownHandling(MagikaTikaDetector.UnknownHandling.OCTET_STREAM)
      .build()) {
      MediaType mediaType = detector.detect(new ByteArrayInputStream(new byte[0]), new Metadata());

      assertThat(mediaType).isEqualTo(MediaType.OCTET_STREAM);
    }
  }

  @Test
  void metadata_emission_can_be_disabled() throws Exception {
    Metadata metadata = new Metadata();
    byte[] bytes = Files.readAllBytes(CORE_FIXTURES.resolve("images/sample.png"));

    try (MagikaTikaDetector detector = MagikaTikaDetector.builder().emitMetadata(false).build()) {
      MediaType mediaType = detector.detect(new ByteArrayInputStream(bytes), metadata);

      assertThat(mediaType).isEqualTo(MediaType.image("png"));
      assertThat(metadata.get(MagikaTikaDetector.MAGIKA_LABEL)).isNull();
    }
  }
}
