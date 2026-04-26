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

package dev.jcputney.magika._meta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Test-scope POJO bound to the v0.2 SUMMARY frontmatter standard (PROC-02). Jackson deserializes
 * via the canonical record constructor; the {@code -parameters} compile flag + explicit
 * {@link JsonProperty} annotations are both load-bearing per the same pattern as
 * {@code config.ContentTypeInfo}.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is mandatory: live SUMMARYs carry many
 * additional non-standardized fields ({@code subsystem}, {@code tags}, {@code dependency_graph},
 * {@code tech_stack}, {@code findings_closed}, {@code key_files}, {@code commits}, {@code metrics})
 * that the PROC-02 lint deliberately does not validate.
 *
 * <p><b>Critical: this record does NOT throw on null required fields.</b> Per B-01, Jackson with
 * {@code ignoreUnknown = true} silently leaves absent or misnamed fields ({@code one-liner:}
 * typo, {@code requirement_completed:} typo) as null after deserialization. The
 * post-deserialization assertion layer in {@link DocConsistencyLint} checks
 * {@code fm.oneLiner() == null} and emits a {@code Failure(file, "MISSING_REQUIRED_FIELD", ...)}
 * record instead of throwing. Calling {@code Objects.requireNonNull} in the compact constructor
 * would abort the scan on the first violator instead of accumulating all failures into a single
 * diagnostic — that pattern is intentionally avoided here.
 *
 * <p><b>CFG-04 ArchUnit carve-out (B-08):</b> {@code PackageBoundaryTest} uses
 * {@code ImportOption.DoNotIncludeTests.class} (see {@code PackageBoundaryTest.java:40}), so
 * this test-scope reference to {@code com.fasterxml.jackson.annotation.*} is excluded from the
 * CFG-04 Jackson-confinement-to-{@code config.*} rule by construction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SummaryFrontmatter(
                                 @JsonProperty("one_liner")
                                 String oneLiner,
                                 @JsonProperty("requirements_completed")
                                 List<String> requirementsCompleted,
                                 @JsonProperty("decisions")
                                 List<DecisionEntry> decisions) {

  /**
   * Compact constructor: defensive immutable copies on collections; <b>no</b> null-handling for
   * required string fields. The {@link DocConsistencyLint} post-deserialization assertion layer
   * is the validation surface (B-01).
   */
  public SummaryFrontmatter {
    requirementsCompleted = requirementsCompleted == null ? List.of() : List.copyOf(requirementsCompleted);
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }

  /**
   * One element of the PROC-03 {@code decisions[]} convention: three required string fields
   * ({@code topic}, {@code decision}, {@code rationale}). All three are required when the
   * containing {@code decisions[]} entry is present; the lint emits {@code DECISIONS_SHAPE} for
   * any null or blank field. {@code topic} is free-text per D-11 — conventionally references a
   * REQ-ID (e.g. {@code "POST-02"}) or a design topic (e.g. {@code "REF-02 + REF-04 pairing"})
   * but the lint does NOT enforce a regex.
   */
  public record DecisionEntry(String topic, String decision, String rationale) {
    // Jackson 2.21.2 + record-component name match for topic/decision/rationale — no
    // @JsonProperty annotations needed. No defensive null-handling for the same reason as
    // SummaryFrontmatter (DocConsistencyLint is the validation surface).
  }
}
