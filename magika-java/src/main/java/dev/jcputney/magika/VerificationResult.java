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

import java.util.Objects;

/**
 * Result of checking a detected content type against an expected allowlist.
 *
 * @param accepted true when the detected type matched the expected allowlist
 * @param reason   machine-readable verification outcome
 * @param detected detected content type
 * @param expected expected allowlist
 */
public record VerificationResult(
                                 boolean accepted,
                                 VerificationReason reason,
                                 DetectedContentType detected,
                                 ExpectedContentTypes expected) {

  public VerificationResult {
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(detected, "detected");
    Objects.requireNonNull(expected, "expected");
  }

  /** Evaluates a detected type against an allowlist. */
  public static VerificationResult evaluate(
    DetectedContentType detected,
    ExpectedContentTypes expected) {
    Objects.requireNonNull(detected, "detected");
    Objects.requireNonNull(expected, "expected");

    if (detected.status() != Status.OK) {
      return new VerificationResult(false, VerificationReason.ERROR_STATUS, detected, expected);
    }

    if (detected.isUnknownLike()) {
      if (expected.matchesMime(detected)) {
        return new VerificationResult(true, VerificationReason.MATCH, detected, expected);
      }
      return new VerificationResult(false, VerificationReason.UNKNOWN_DETECTION, detected, expected);
    }

    if (expected.matches(detected)) {
      return new VerificationResult(true, VerificationReason.MATCH, detected, expected);
    }
    return new VerificationResult(false, VerificationReason.MISMATCH, detected, expected);
  }
}
