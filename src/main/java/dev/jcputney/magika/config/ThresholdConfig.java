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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed {@code config.min.json} (CFG-01, CFG-03). Binds the 12 parity-relevant top-level keys of
 * the upstream {@code standard_v3_3/config.min.json}; forward-compat-tolerated additions are
 * allowed by Jackson's {@code FAIL_ON_UNKNOWN_PROPERTIES=false} (see {@link MagikaConfigLoader}
 * and PITFALLS.md §Pitfall 9).
 *
 * <p>Component names camelCase, JSON keys snake_case via explicit {@link JsonProperty} — per
 * PITFALLS.md §Pitfall 9 the explicit annotation + {@code -parameters} compile flag is the
 * robust Jackson-record pattern on Jackson 2.21.x.
 *
 * <p>Cite: {@code docs/algorithm-notes.md} §"config.min.json schema" for field semantics. The
 * reserved {@code protection} and {@code aes_key_hex} upstream fields are NOT bound here; Plan 4
 * reads them separately at model-load time.
 *
 * @param begSize                   tokens kept from beg window after strip (1024 for standard_v3_3)
 * @param midSize                   tokens from middle (0 for standard_v3_3)
 * @param endSize                   tokens kept from end window after strip (1024 for standard_v3_3)
 * @param blockSize                 raw byte-window size before strip (4096 for standard_v3_3)
 * @param paddingToken              sentinel token for under-size slices (256 for standard_v3_3)
 * @param minFileSizeForDl          below this size skip the model (8 for standard_v3_3)
 * @param mediumConfidenceThreshold scalar threshold for MEDIUM_CONFIDENCE mode + fallback in
 *                                  HIGH_CONFIDENCE
 * @param useInputsAtOffsets        always false in Phase 1 (asserted by feature extractor)
 * @param targetLabelsSpace         the model's full softmax output label vocabulary
 * @param thresholds                per-type HIGH_CONFIDENCE threshold overrides
 * @param overwriteMap              applied BEFORE threshold check (see §Overwrite-map ordering)
 * @param versionMajor              config schema major version (3 for standard_v3_3)
 */
public record ThresholdConfig(
                              @JsonProperty("beg_size")
                              int begSize,
                              @JsonProperty("mid_size")
                              int midSize,
                              @JsonProperty("end_size")
                              int endSize,
                              @JsonProperty("block_size")
                              int blockSize,
                              @JsonProperty("padding_token")
                              int paddingToken,
                              @JsonProperty("min_file_size_for_dl")
                              int minFileSizeForDl,
                              @JsonProperty("medium_confidence_threshold")
                              double mediumConfidenceThreshold,
                              @JsonProperty("use_inputs_at_offsets")
                              boolean useInputsAtOffsets,
                              @JsonProperty("target_labels_space")
                              List<String> targetLabelsSpace,
                              @JsonProperty("thresholds")
                              Map<String, Double> thresholds,
                              @JsonProperty("overwrite_map")
                              Map<String, String> overwriteMap,
                              @JsonProperty("version_major")
                              int versionMajor) {

  public ThresholdConfig {
    Objects.requireNonNull(targetLabelsSpace, "targetLabelsSpace");
    Objects.requireNonNull(thresholds, "thresholds");
    Objects.requireNonNull(overwriteMap, "overwriteMap");
    targetLabelsSpace = List.copyOf(targetLabelsSpace);
    thresholds = Map.copyOf(thresholds);
    overwriteMap = Map.copyOf(overwriteMap);
  }
}
