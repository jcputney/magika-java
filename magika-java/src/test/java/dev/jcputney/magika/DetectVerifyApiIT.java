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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("parity")
class DetectVerifyApiIT {

  private static final Path FIXTURES_ROOT = Path.of("src/test/resources/fixtures");

  @Test
  void detectPath_returns_mime_focused_metadata() {
    try (Magika m = Magika.create()) {
      DetectedContentType detected = m.detectPath(FIXTURES_ROOT.resolve("images/sample.png"));

      assertThat(detected.label()).isEqualTo("png");
      assertThat(detected.mimeType()).isEqualTo("image/png");
      assertThat(detected.group()).isEqualTo("image");
      assertThat(detected.extensions()).contains("png");
      assertThat(detected.status()).isEqualTo(Status.OK);
    }
  }

  @Test
  void detectStream_uses_same_metadata_shape() {
    try (Magika m = Magika.create()) {
      DetectedContentType detected =
        m.detectStream(new ByteArrayInputStream("<html><body>Hello</body></html>".getBytes()));

      assertThat(detected.label()).isNotBlank();
      assertThat(detected.status()).isEqualTo(Status.OK);
      assertThat(detected.score()).isBetween(0.0, 1.0001);
    }
  }

  @Test
  void verifyPath_accepts_group_constant() {
    try (Magika m = Magika.create()) {
      VerificationResult result =
        m.verifyPath(FIXTURES_ROOT.resolve("images/sample.png"), ExpectedContentTypes.IMAGE);

      assertThat(result.accepted()).isTrue();
      assertThat(result.reason()).isEqualTo(VerificationReason.MATCH);
      assertThat(result.detected().label()).isEqualTo("png");
    }
  }

  @Test
  void verifyPath_rejects_wrong_group() {
    try (Magika m = Magika.create()) {
      VerificationResult result =
        m.verifyPath(FIXTURES_ROOT.resolve("images/sample.png"), ExpectedContentTypes.VIDEO);

      assertThat(result.accepted()).isFalse();
      assertThat(result.reason()).isEqualTo(VerificationReason.MISMATCH);
    }
  }

  @Test
  void verifyBytes_treats_empty_as_unknown_unless_explicit_mime_allowed() {
    try (Magika m = Magika.create()) {
      VerificationResult imageResult = m.verifyBytes(new byte[0], ExpectedContentTypes.IMAGE);
      VerificationResult emptyMimeResult =
        m.verifyBytes(new byte[0], ExpectedContentTypes.ofMimeTypes("inode/x-empty"));

      assertThat(imageResult.accepted()).isFalse();
      assertThat(imageResult.reason()).isEqualTo(VerificationReason.UNKNOWN_DETECTION);
      assertThat(emptyMimeResult.accepted()).isTrue();
      assertThat(emptyMimeResult.reason()).isEqualTo(VerificationReason.MATCH);
    }
  }

  @Test
  void verify_rejects_null_expected_before_detection() {
    try (Magika m = Magika.create()) {
      assertThatThrownBy(() -> m.verifyBytes(new byte[] {'a'}, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("expected");
    }
  }
}
