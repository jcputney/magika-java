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

/** Outcome reason for an allowlist verification. */
public enum VerificationReason {
  /** The detected MIME type or group matched the expected allowlist. */
  MATCH,

  /** Detection succeeded, but the detected MIME type and group were not allowed. */
  MISMATCH,

  /** Detection produced a sentinel such as empty, unknown, or undefined. */
  UNKNOWN_DETECTION,

  /** Detection returned a non-OK status. */
  ERROR_STATUS
}
