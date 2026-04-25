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

package dev.jcputney.magika.parity;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jcputney.magika.postprocess.OverwriteReason;
import java.util.Objects;

/**
 * Deserialization target for the per-fixture sidecar {@code .expected.json} files (D-04).
 *
 * <p>Schema:
 *
 * <pre>
 * {
 * "dl": { "label": "...", "score": 0.9876, "overwriteReason": "NONE" },
 * "output": { "label": "...", "score": 0.9876, "overwriteReason": "NONE|LOW_CONFIDENCE|OVERWRITE_MAP" },
 * "score": 0.9876,
 * "upstream_magika_version": "1.0.2",
 * "upstream_magika_git_sha": "363a441..."
 * }
 * </pre>
 *
 * <p>This record lives under {@code src/test/java/}; the ArchUnit {@code jacksonConfinedToConfig}
 * rule uses {@code ImportOption.DoNotIncludeTests} so test-scope Jackson imports are permitted.
 */
public record ExpectedResult(
                             @JsonProperty("dl")
                             ExpectedPrediction dl,
                             @JsonProperty("output")
                             ExpectedPrediction output,
                             @JsonProperty("score")
                             double score,
                             @JsonProperty("upstream_magika_version")
                             String upstreamMagikaVersion,
                             @JsonProperty("upstream_magika_git_sha")
                             String upstreamMagikaGitSha) {

  public ExpectedResult {
    Objects.requireNonNull(dl, "dl");
    Objects.requireNonNull(output, "output");
  }

  /** One prediction column from the sidecar (either {@code dl} or {@code output}). */
  public record ExpectedPrediction(
                                   @JsonProperty("label")
                                   String label,
                                   @JsonProperty("score")
                                   double score,
                                   @JsonProperty("overwriteReason")
                                   String overwriteReason) {

    /** Convenience: parses the string reason back to the Java enum. */
    public OverwriteReason overwriteReasonEnum() {
      return overwriteReasonEnum(null);
    }

    /**
     * Fixture-aware variant — surfaces the fixture path in the failure message when the sidecar
     * carries a value that doesn't map to {@link OverwriteReason} (DEBT-02 IN-03).
     */
    public OverwriteReason overwriteReasonEnum(java.nio.file.Path fixture) {
      if (overwriteReason == null) {
        return OverwriteReason.NONE;
      }
      try {
        return OverwriteReason.valueOf(overwriteReason);
      } catch (IllegalArgumentException e) {
        String suffix = fixture == null ? "" : " in sidecar for fixture: " + fixture;
        throw new AssertionError(
          "Unrecognized overwriteReason '" + overwriteReason + "'" + suffix, e);
      }
    }
  }
}
