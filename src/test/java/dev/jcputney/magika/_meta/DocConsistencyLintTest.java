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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PROC-01 lint test: verifies {@link DocConsistencyLint#scan(Path)} catches stale REQUIREMENTS.md
 * checkboxes, frontmatter shape violations, and orphan REQ-IDs across an active-milestone corpus.
 *
 * <p><b>BUILD-08 contract:</b> when {@code .planning/} is absent (CI runners), the active-corpus
 * canary test reports JUnit SKIPPED via {@link Assumptions#assumeTrue}. Inline {@code @TempDir}
 * fixture tests run unconditionally — they are hermetic and exercise the lint logic itself,
 * independent of the live corpus shape. Verify the skip-contract manually:
 * {@code mv .planning .planning.bak && mvn -B -ntp test -Dtest=DocConsistencyLintTest && mv .planning.bak .planning}
 * — expected output line: {@code Tests run: N, Failures: 0, Errors: 0, Skipped: 1}.
 *
 * <p><b>B-06 wording note:</b> PROC-01's REQUIREMENTS.md text says "informational log line".
 * The actual mechanism is a JUnit test-report SKIPPED entry visible in
 * {@code target/surefire-reports/TEST-DocConsistencyLintTest.xml} as
 * {@code <skipped message="...BUILD-08..."/>} — NOT an SLF4J INFO log line. The SKIPPED entry
 * is the more-precise audit artifact.
 *
 * <p><b>P2 — use @Test not @TestFactory:</b> {@code Assumptions.assumeTrue} at the
 * {@code @TestFactory} factory-method level produces a vacuous PASS (zero DynamicTests
 * generated, factory itself reports as PASSED). REQUIREMENTS.md PROC-01 explicitly forbids
 * vacuous pass. All tests below are {@code @Test} methods.
 *
 * <p><b>CFG-04 carve-out (B-08):</b> {@code PackageBoundaryTest} uses
 * {@code ImportOption.DoNotIncludeTests.class}, so this test-scope reference to
 * {@code com.fasterxml.jackson.dataformat.yaml.*} (transitively via {@link DocConsistencyLint})
 * is excluded from the Jackson-confinement rule by construction.
 *
 * <p><b>Working-directory assumption (BUILD-08 corollary):</b> The {@code active_corpus_is_clean()}
 * test uses {@code Path.of(".planning")} which resolves relative to the JVM working directory.
 * Maven Surefire sets {@code basedir == project root} (verified at {@code mvn test} time), so the
 * relative path resolves correctly under normal Maven invocation. If this test is ever run from a
 * subdirectory (e.g., an IDE test runner with a non-root working directory, or a future multi-module
 * Maven reorg), {@code Path.of(".planning")} will resolve to the wrong location and the test will
 * throw {@code NoSuchFileException} BEFORE the {@link org.junit.jupiter.api.Assumptions#assumeTrue}
 * skip guard fires — defeating the BUILD-08 skip-when-absent contract. If this assumption ever
 * changes, replace {@code Path.of(".planning")} with a system-property-resolved absolute path and
 * update this Javadoc + 04-VALIDATION.md manual probe instructions.
 */
@Tag("unit")
class DocConsistencyLintTest {

  // ----------------------------------------------------------------------------------------
  // Hermetic inline @TempDir fixtures — run unconditionally (no Assumptions guard needed).
  // Each test constructs a synthetic .planning/-shaped tree under @TempDir and asserts the
  // expected Failure mode is present (or absence of failures for happy paths).
  // ----------------------------------------------------------------------------------------

  @Test
  void parses_canonical_frontmatter(@TempDir
  Path tmp) throws IOException {
    writeRequirements(tmp, "- [x] **FOO-1**: synthetic\n");
    writeSummary(tmp, "01-fake/01-fake-SUMMARY.md",
        frontmatter("synthetic plan", "[FOO-1]", null));

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures()).isEmpty();
  }

  @Test
  void lint_catches_stale_checkbox_forward(@TempDir
  Path tmp) throws IOException {
    // D-12 canonical fixture: REQUIREMENTS still '[ ]' but SUMMARY claims completed.
    writeRequirements(tmp, "- [ ] **FAKE-99**: synthetic unfinished\n");
    writeSummary(tmp, "99-fake/99-fake-SUMMARY.md",
        frontmatter("synthetic plan", "[FAKE-99]", null));

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::mode)
        .contains("DRIFT_STALE_FORWARD");
    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::message)
        .anyMatch(m -> m.contains("FAKE-99"));
  }

  @Test
  void lint_catches_stale_checkbox_reverse(@TempDir
  Path tmp) throws IOException {
    writeRequirements(tmp, "- [x] **LONELY-1**: shipped but never claimed\n");
    // No SUMMARY claims LONELY-1.
    Files.createDirectories(tmp.resolve("phases"));

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::mode)
        .contains("DRIFT_STALE_REVERSE");
    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::message)
        .anyMatch(m -> m.contains("LONELY-1"));
  }

  @Test
  void lint_catches_orphan(@TempDir
  Path tmp) throws IOException {
    writeRequirements(tmp, "- [x] **REAL-1**: real requirement\n");
    writeSummary(tmp, "01-real/01-real-SUMMARY.md",
        frontmatter("real plan", "[REAL-1, PHANTOM-1]", null));

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::mode)
        .contains("DRIFT_ORPHAN");
    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::message)
        .anyMatch(m -> m.contains("PHANTOM-1"));
  }

  @Test
  void lint_catches_parse_error_missing_closing_delimiter(@TempDir
  Path tmp) throws IOException {
    writeRequirements(tmp, "- [x] **FOO-1**: synthetic\n");
    // Open --- but no closing --- before EOF.
    writeSummary(tmp, "01-bad/01-bad-SUMMARY.md",
        "---\none_liner: \"missing close delim\"\nrequirements_completed: [FOO-1]\n");

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::mode)
        .contains("PARSE_ERROR");
  }

  @Test
  void lint_catches_missing_required_field_one_liner(@TempDir
  Path tmp) throws IOException {
    writeRequirements(tmp, "- [x] **FOO-1**: synthetic\n");
    // Typo: one-liner (hyphen) instead of one_liner (underscore). Jackson @JsonProperty
    // mapping is on 'one_liner' so the typo silently nulls the field per B-01; the
    // post-deserialization assertion layer catches it as MISSING_REQUIRED_FIELD.
    writeSummary(tmp, "01-typo/01-typo-SUMMARY.md",
        "---\none-liner: \"typo on field name\"\nrequirements_completed: [FOO-1]\n---\n");

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::mode)
        .contains("MISSING_REQUIRED_FIELD");
    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::message)
        .anyMatch(m -> m.contains("one_liner"));
  }

  @Test
  void lint_catches_one_liner_too_long(@TempDir
  Path tmp) throws IOException {
    writeRequirements(tmp, "- [x] **FOO-1**: synthetic\n");
    // Build a 121-char one_liner (1 over the 120 limit).
    String tooLong = "x".repeat(121);
    writeSummary(tmp, "01-long/01-long-SUMMARY.md",
        "---\none_liner: \"" + tooLong + "\"\nrequirements_completed: [FOO-1]\n---\n");

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::mode)
        .contains("ONE_LINER_TOO_LONG");
  }

  @Test
  void lint_catches_req_id_shape(@TempDir
  Path tmp) throws IOException {
    writeRequirements(tmp, "- [x] **FOO-1**: synthetic\n");
    // Lowercase REQ-ID violates ^[A-Z]+-[0-9]+$.
    writeSummary(tmp, "01-shape/01-shape-SUMMARY.md",
        frontmatter("bad req shape", "[\"foo-1\"]", null));

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::mode)
        .contains("REQ_ID_SHAPE");
  }

  @Test
  void lint_catches_decisions_shape(@TempDir
  Path tmp) throws IOException {
    writeRequirements(tmp, "- [x] **FOO-1**: synthetic\n");
    // Blank 'decision' field — DECISIONS_SHAPE per D-11.
    String decisions =
        "  - topic: \"a topic\"\n"
            + "    decision: \"\"\n"
            + "    rationale: \"a rationale\"\n";
    writeSummary(tmp, "01-dshape/01-dshape-SUMMARY.md",
        "---\n"
            + "one_liner: \"bad decisions shape\"\n"
            + "requirements_completed: [FOO-1]\n"
            + "decisions:\n" + decisions
            + "---\n");

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures())
        .extracting(DocConsistencyLint.Failure::mode)
        .contains("DECISIONS_SHAPE");
  }

  @Test
  void absent_decisions_field_does_not_violate(@TempDir
  Path tmp) throws IOException {
    // B-11: decisions[] is OPTIONAL — absence is not MISSING_REQUIRED_FIELD.
    writeRequirements(tmp, "- [x] **FOO-1**: synthetic\n");
    writeSummary(tmp, "01-nodec/01-nodec-SUMMARY.md",
        "---\none_liner: \"no decisions field\"\nrequirements_completed: [FOO-1]\n---\n");

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures()).isEmpty();
  }

  @Test
  void scanner_skips_milestones_subtree(@TempDir
  Path tmp) throws IOException {
    // P3: a malformed SUMMARY under phases/.../milestones/ MUST NOT surface failures —
    // the milestones subtree is read-only audit material per D-05.
    writeRequirements(tmp, "- [x] **FOO-1**: synthetic\n");
    writeSummary(tmp, "01-real/01-real-SUMMARY.md",
        frontmatter("clean plan", "[FOO-1]", null));
    // Synthesize a malformed SUMMARY inside a milestones/ subdirectory.
    writeSummary(tmp, "01-real/milestones/v0.1-phases/01-archived-SUMMARY.md",
        "no frontmatter at all — would be PARSE_ERROR if scanned\n");

    DocConsistencyLint.Report report = DocConsistencyLint.scan(tmp);

    assertThat(report.failures()).isEmpty();
  }

  // ----------------------------------------------------------------------------------------
  // Active-corpus canary — guarded by Assumptions.assumeTrue per BUILD-08 (D-10).
  // The Assumptions.assumeTrue MUST be the first executable line. JUnit reports SKIPPED
  // (NOT passed, NOT failed, NOT vacuous-passed) when .planning/ is absent.
  // ----------------------------------------------------------------------------------------

  @Test
  void active_corpus_is_clean() throws IOException {
    Assumptions.assumeTrue(
        Files.isDirectory(Path.of(".planning")),
        ".planning/ absent — lint runs at milestone close on planner's machine; "
            + "CI runners log skip and move on (BUILD-08).");
    // BUILD-08 corollary: in worktree-parallel-execution mode the worktree contains a
    // PARTIAL .planning/ — only the gitignored files committed in prior waves are present;
    // REQUIREMENTS.md is gitignored at the project root and may not exist in the worktree's
    // copy of .planning/ at all. Without REQUIREMENTS.md the lint cannot validate
    // SUMMARY-claimed REQ-IDs, so it would surface every claimed REQ-ID as DRIFT_ORPHAN —
    // a noisy false-positive that masks real drift. Skip in this case for the same reason
    // we skip when the whole .planning/ is absent: the corpus state is not validatable.
    Assumptions.assumeTrue(
        Files.isRegularFile(Path.of(".planning/REQUIREMENTS.md")),
        ".planning/REQUIREMENTS.md absent — partial .planning/ state (likely worktree "
            + "parallel-execution mode where REQUIREMENTS.md is not committed). The "
            + "orchestrator runs the canary post-merge against the full main-repo .planning/.");

    DocConsistencyLint.Report report = DocConsistencyLint.scan(Path.of(".planning"));

    // Per B-05 dogfood timing: this is the moment 04-01's normalization is validated. If 04-01
    // missed a SUMMARY or a field, this assertion lists every drift entry in one diagnostic.
    assertThat(report.failures())
        .as("Doc-consistency lint failures (PROC-01) — fix this list at milestone close")
        .isEmpty();
  }

  // ----------------------------------------------------------------------------------------
  // Helpers — keep individual tests focused on the failure mode under test.
  // ----------------------------------------------------------------------------------------

  private static void writeRequirements(Path tmp, String body) throws IOException {
    Files.writeString(tmp.resolve("REQUIREMENTS.md"), body);
  }

  private static void writeSummary(Path tmp, String relativePath, String content)
      throws IOException {
    Path summary = tmp.resolve("phases").resolve(relativePath);
    Files.createDirectories(summary.getParent());
    Files.writeString(summary, content);
  }

  /**
   * Build a canonical-shape frontmatter block.
   *
   * @param oneLiner the one_liner value (no surrounding quotes — they are added).
   * @param requirementsList the requirements_completed value (e.g. {@code "[FOO-1]"}).
   * @param decisionsBlock optional indented decisions YAML block (without the leading
   *                       {@code "decisions:\n"}). Pass null to omit the field entirely.
   */
  private static String frontmatter(String oneLiner, String requirementsList,
      String decisionsBlock) {
    StringBuilder sb = new StringBuilder();
    sb.append("---\n");
    sb.append("one_liner: \"").append(oneLiner).append("\"\n");
    sb.append("requirements_completed: ").append(requirementsList).append("\n");
    if (decisionsBlock != null) {
      sb.append("decisions:\n").append(decisionsBlock);
    }
    sb.append("---\n");
    return sb.toString();
  }
}
