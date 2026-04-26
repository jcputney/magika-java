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

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * PROC-01 doc-consistency lint. Walks {@code planningRoot/phases/}{@code **}/*SUMMARY.md, parses
 * YAML frontmatter via Jackson YAML, and accumulates {@link Failure} records into an immutable
 * {@link Report}.
 *
 * <p><b>No direct project analog:</b> this is a pure static method over filesystem state, a
 * build-time tool not a domain class. Stylistic neighbor is {@code parity.FixtureLoader} (also
 * static-utility, also walks a resource tree); data flow differs — the lint reads the planning
 * corpus, fixtures are bundled test resources.
 *
 * <p><b>Six failure modes</b> (D-09 + B-07): {@code PARSE_ERROR}, {@code MISSING_REQUIRED_FIELD},
 * {@code ONE_LINER_TOO_LONG}, {@code REQ_ID_SHAPE}, {@code DECISIONS_SHAPE}, plus three drift
 * directions {@code DRIFT_STALE_FORWARD} (SUMMARY claims a REQ but REQUIREMENTS still
 * {@code [ ]}), {@code DRIFT_STALE_REVERSE} (REQUIREMENTS {@code [x]} but no SUMMARY claims it),
 * {@code DRIFT_ORPHAN} (SUMMARY claims a REQ-ID that doesn't exist in REQUIREMENTS).
 *
 * <p><b>BUILD-08 contract:</b> the caller is responsible for guarding the live-corpus call site
 * with {@code Assumptions.assumeTrue(Files.isDirectory(planningRoot))}. The scanner itself does
 * NOT short-circuit on missing {@code planningRoot} — it would throw
 * {@link java.nio.file.NoSuchFileException}. This separation keeps the scanner pure (testable
 * with arbitrary tmp directories per D-12) while the test class enforces skip-when-absent via
 * JUnit {@code Assumptions.assumeTrue} (D-10).
 *
 * <p><b>D-05 invariant:</b> scanner walks {@code planningRoot/phases/} only, NOT
 * {@code planningRoot/}. This excludes the v0.1 archive at
 * {@code .planning/milestones/v0.1-phases/} by construction (P3 belt-and-suspenders
 * explicit-skip filter retained for defense-in-depth).
 */
public final class DocConsistencyLint {

  /**
   * Pinned active-checkbox-line REQ-ID extraction regex (D-08 + B-04). Matches
   * {@code - [x] **DEBT-01**} and {@code - [ ] **PROC-01**}; does NOT match the
   * {@code Future Requirements} section ({@code - **REL-10**:} — no checkbox brackets) or the
   * Traceability table ({@code |}-delimited cells).
   */
  private static final Pattern REQ_ID_LINE =
      Pattern.compile("^\\- \\[.\\] \\*\\*([A-Z]+-[0-9]+)\\*\\*");

  /** Per-REQ-ID shape regex for D-09 {@code REQ_ID_SHAPE} validation. */
  private static final Pattern REQ_ID_SHAPE = Pattern.compile("^[A-Z]+-[0-9]+$");

  private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

  private DocConsistencyLint() {
    // static utility
  }

  /** A single lint violation, named per D-09 failure mode. */
  public record Failure(String filePath, String mode, String message) {
  }

  /** Immutable scan result; {@link Report#failures()} is empty when the corpus is clean. */
  public record Report(List<Failure> failures) {
    public Report {
      failures = List.copyOf(failures);
    }
  }

  /**
   * Scan the given planning root for PROC-02 standard violations and PROC-01 drift.
   *
   * @param planningRoot path to the {@code .planning/} directory (or a synthetic equivalent for
   *                     tests). MUST exist; the caller is responsible for the BUILD-08
   *                     skip-when-absent guard via {@code Assumptions.assumeTrue}.
   * @return immutable Report of all accumulated violations across the corpus.
   * @throws IOException on filesystem read failure (NOT on per-file YAML parse failure — those
   *                     are recorded as {@code PARSE_ERROR} Failures).
   */
  public static Report scan(Path planningRoot) throws IOException {
    Objects.requireNonNull(planningRoot, "planningRoot");

    List<Failure> failures = new ArrayList<>();

    // 1. Extract REQ-IDs from REQUIREMENTS.md via the pinned active-checkbox regex.
    Path reqsFile = planningRoot.resolve("REQUIREMENTS.md");
    Set<String> requirementsChecked = new LinkedHashSet<>();
    Set<String> requirementsUnchecked = new LinkedHashSet<>();
    if (Files.isRegularFile(reqsFile)) {
      for (String line : Files.readAllLines(reqsFile)) {
        Matcher m = REQ_ID_LINE.matcher(line);
        if (m.find()) {
          String reqId = m.group(1);
          boolean checked = line.startsWith("- [x]") || line.startsWith("- [X]");
          if (checked) {
            requirementsChecked.add(reqId);
          } else {
            requirementsUnchecked.add(reqId);
          }
        }
      }
    }
    Set<String> allKnownReqIds = new LinkedHashSet<>(requirementsChecked);
    allKnownReqIds.addAll(requirementsUnchecked);

    // 2. Walk planningRoot/phases/**/*SUMMARY.md (D-08 amended per B-09; D-05 + P3 — never
    //    descend into milestones/).
    Path phasesRoot = planningRoot.resolve("phases");
    Set<String> claimedReqIds = new LinkedHashSet<>();
    if (Files.isDirectory(phasesRoot)) {
      // Glob `**/*SUMMARY.md` matches across all directory boundaries — works on both absolute
      // paths (returned by Files.walk(phasesRoot)) and relative paths. Do not relativize before
      // matching.
      PathMatcher summaryMatcher =
          FileSystems.getDefault().getPathMatcher("glob:**/*SUMMARY.md");
      List<Path> summaries;
      try (Stream<Path> walk = Files.walk(phasesRoot)) {
        summaries = walk
            .filter(Files::isRegularFile)
            .filter(summaryMatcher::matches)
            .filter(p -> !p.toString().contains("/milestones/"))
            .sorted()
            .toList();
      }

      for (Path summaryFile : summaries) {
        Optional<SummaryFrontmatter> parsed = parseFrontmatter(summaryFile, failures);
        if (parsed.isEmpty()) {
          continue;
        }
        SummaryFrontmatter fm = parsed.get();
        String relPath = planningRoot.relativize(summaryFile).toString();

        // 3. Post-deserialization assertion layer (D-02 + B-01).
        if (fm.oneLiner() == null || fm.oneLiner().isBlank()) {
          failures.add(new Failure(relPath, "MISSING_REQUIRED_FIELD",
              "'one_liner' absent or misnamed (e.g. did you write 'one-liner' or 'oneLiner'?)"));
        } else if (fm.oneLiner().length() > 120) {
          failures.add(new Failure(relPath, "ONE_LINER_TOO_LONG",
              "one_liner length=" + fm.oneLiner().length() + " (limit 120)"));
        }

        // requirementsCompleted is normalized to List.of() by the compact constructor in
        // SummaryFrontmatter, so an absent OR misnamed field deserializes as an empty list — both
        // surface here as MISSING_REQUIRED_FIELD.
        if (fm.requirementsCompleted().isEmpty()) {
          failures.add(new Failure(relPath, "MISSING_REQUIRED_FIELD",
              "'requirements_completed' absent, misnamed, or empty"));
        } else {
          for (String reqId : fm.requirementsCompleted()) {
            if (reqId == null || !REQ_ID_SHAPE.matcher(reqId).matches()) {
              failures.add(new Failure(relPath, "REQ_ID_SHAPE",
                  "requirements_completed entry '" + reqId
                      + "' does not match ^[A-Z]+-[0-9]+$"));
            } else {
              claimedReqIds.add(reqId);
              // 4. Drift detection (D-06): forward + orphan, per claim.
              if (!allKnownReqIds.contains(reqId)) {
                failures.add(new Failure(relPath, "DRIFT_ORPHAN",
                    "requirements_completed entry '" + reqId
                        + "' does not appear in REQUIREMENTS.md active checkbox section"));
              } else if (requirementsUnchecked.contains(reqId)) {
                failures.add(new Failure(relPath, "DRIFT_STALE_FORWARD",
                    "REQ-ID '" + reqId + "' claimed by SUMMARY but REQUIREMENTS.md still '[ ]'"));
              }
            }
          }
        }

        // decisions[] is OPTIONAL per B-11 — absence is not a violation. When present, every
        // entry MUST have non-blank topic / decision / rationale (D-11).
        if (fm.decisions() != null) {
          for (int i = 0; i < fm.decisions().size(); i++) {
            SummaryFrontmatter.DecisionEntry e = fm.decisions().get(i);
            if (e == null || e.topic() == null || e.topic().isBlank()
                || e.decision() == null || e.decision().isBlank()
                || e.rationale() == null || e.rationale().isBlank()) {
              failures.add(new Failure(relPath, "DECISIONS_SHAPE",
                  "decisions[" + i + "] missing or blank topic/decision/rationale"));
            }
          }
        }
      }
    }

    // 5. Drift detection (D-06): reverse direction (REQUIREMENTS [x] but no SUMMARY claims).
    for (String checkedReq : requirementsChecked) {
      if (!claimedReqIds.contains(checkedReq)) {
        failures.add(new Failure("REQUIREMENTS.md", "DRIFT_STALE_REVERSE",
            "REQ-ID '" + checkedReq + "' is '[x]' in REQUIREMENTS.md but no SUMMARY's"
                + " requirements_completed lists it"));
      }
    }

    return new Report(failures);
  }

  /**
   * Extract YAML frontmatter from a SUMMARY file and parse into a {@link SummaryFrontmatter}.
   * Records {@code PARSE_ERROR} on malformed frontmatter or YAML mapping failure (B-07).
   */
  private static Optional<SummaryFrontmatter> parseFrontmatter(
      Path summaryFile, List<Failure> failures) throws IOException {
    String content = Files.readString(summaryFile);
    if (!content.startsWith("---\n")) {
      failures.add(new Failure(summaryFile.toString(), "PARSE_ERROR",
          "Frontmatter missing opening '---' delimiter at file head"));
      return Optional.empty();
    }
    int secondDelim = content.indexOf("\n---\n", 4);
    if (secondDelim < 0) {
      int eofDelim = content.indexOf("\n---", 4);
      if (eofDelim < 0 || eofDelim != content.length() - 4) {
        failures.add(new Failure(summaryFile.toString(), "PARSE_ERROR",
            "Frontmatter missing closing '---' delimiter"));
        return Optional.empty();
      }
      secondDelim = eofDelim;
    }
    String yaml = content.substring(4, secondDelim);
    try {
      return Optional.of(YAML_MAPPER.readValue(yaml, SummaryFrontmatter.class));
    } catch (JsonMappingException e) {
      String msg = e.getOriginalMessage() != null ? e.getOriginalMessage() : e.getMessage();
      failures.add(new Failure(summaryFile.toString(), "PARSE_ERROR", msg));
      return Optional.empty();
    }
  }
}
