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

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ExpectedContentTypesTest {

  @Test
  void constants_match_by_group() {
    DetectedContentType png =
      new DetectedContentType("png", "image/png", "image", "PNG", List.of("png"), false, 1.0, Status.OK);

    assertThat(ExpectedContentTypes.IMAGE.matches(png)).isTrue();
    assertThat(ExpectedContentTypes.VIDEO.matches(png)).isFalse();
  }

  @Test
  void exact_mime_matching_is_case_insensitive() {
    DetectedContentType pdf = new DetectedContentType(
      "pdf",
      "application/pdf",
      "document",
      "PDF",
      List.of("pdf"),
      false,
      1.0,
      Status.OK);

    assertThat(ExpectedContentTypes.ofMimeTypes("APPLICATION/PDF").matches(pdf)).isTrue();
  }

  @Test
  void anyOf_unions_mimes_and_groups() {
    ExpectedContentTypes expected = ExpectedContentTypes.anyOf(
      ExpectedContentTypes.IMAGE,
      ExpectedContentTypes.ofMimeTypes("application/pdf"));

    assertThat(expected.groups()).containsExactly("image");
    assertThat(expected.mimeTypes()).containsExactly("application/pdf");
  }

  @Test
  void blank_values_are_rejected() {
    assertThatThrownBy(() -> ExpectedContentTypes.ofMimeTypes(" "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must not be blank");
  }

  @Test
  void nonOk_status_evaluates_as_error_status() {
    DetectedContentType error = new DetectedContentType(
      "unknown",
      "application/octet-stream",
      "unknown",
      "Unknown",
      List.of(),
      false,
      0.0,
      Status.UNKNOWN);

    VerificationResult result = VerificationResult.evaluate(error, ExpectedContentTypes.ofMimeTypes(
      "application/octet-stream"));

    assertThat(result.accepted()).isFalse();
    assertThat(result.reason()).isEqualTo(VerificationReason.ERROR_STATUS);
  }

  @Test
  void unknownLike_detection_only_passes_on_explicit_mime_match() {
    DetectedContentType empty =
      new DetectedContentType("empty", "inode/x-empty", "inode", "Empty", List.of(), false, 1.0, Status.OK);

    assertThat(VerificationResult.evaluate(empty, ExpectedContentTypes.ofGroups("inode")).accepted())
      .isFalse();
    assertThat(VerificationResult.evaluate(empty, ExpectedContentTypes.ofGroups("inode")).reason())
      .isEqualTo(VerificationReason.UNKNOWN_DETECTION);
    assertThat(VerificationResult.evaluate(empty, ExpectedContentTypes.ofMimeTypes("inode/x-empty")).accepted())
      .isTrue();
  }

  @Test
  void constructor_copies_sets() {
    Set<String> mimes = new java.util.LinkedHashSet<>();
    mimes.add("image/png");
    ExpectedContentTypes expected = new ExpectedContentTypes(mimes, Set.of());

    mimes.add("application/pdf");

    assertThat(expected.mimeTypes()).containsExactly("image/png");
  }
}
