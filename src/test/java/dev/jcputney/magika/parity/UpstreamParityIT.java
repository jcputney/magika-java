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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import dev.jcputney.magika.Magika;
import dev.jcputney.magika.MagikaResult;
import dev.jcputney.magika.postprocess.PredictionMode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Phase 1 parity harness (TEST-04, TEST-05, TEST-08, TEST-09).
 *
 * <p>Discovers every fixture under {@code src/test/resources/fixtures/} with a sibling
 * {@code .expected.json} and emits one {@link DynamicTest} per fixture — IDE-visible,
 * individually re-runnable.
 *
 * <p>Assertion per TEST-04: strict equality on {@code output.label} AND {@code dl.label};
 * score within {@code 1e-4} tolerance. No test may be disabled, no assumption-skip may silently
 * exclude a fixture, and no tolerance loosening below 1e-4 is permitted (CLAUDE.md
 * non-negotiable — a parity disagreement is a test failure, full stop).
 *
 * <p>Failure message per TEST-05 / TEST-08: fixture name, expected, actual, oracle version +
 * SHA, reproducer one-liner.
 *
 * <p>Tagged {@code @Tag("parity")} so Failsafe runs it (Surefire excludes {@code parity,slow}).
 */
@Tag("parity")
class UpstreamParityIT {

  private static final Path FIXTURES_ROOT = Paths.get("src/test/resources/fixtures");
  private static final double SCORE_TOLERANCE = 1e-4;

  // TEST-12 / TEST-13 (Plan 02-02): three Magika singletons — one per PredictionMode. Native
  // session creation runs three times at suite start (~hundreds of ms × 3, one-time cost). The
  // existing default-mode harness moves to MAGIKA_HIGH (renamed for clarity); MAGIKA_MEDIUM and
  // MAGIKA_BEST drive the new mode-divergence fixtures. See 02-RESEARCH.md §"Pattern 5".
  private static Magika MAGIKA_HIGH;
  private static Magika MAGIKA_MEDIUM;
  private static Magika MAGIKA_BEST;
  private static String ORACLE_PIN;

  @BeforeAll
  static void setup() throws IOException {
    MAGIKA_HIGH = Magika.builder().predictionMode(PredictionMode.HIGH_CONFIDENCE).build();
    MAGIKA_MEDIUM = Magika.builder().predictionMode(PredictionMode.MEDIUM_CONFIDENCE).build();
    MAGIKA_BEST = Magika.builder().predictionMode(PredictionMode.BEST_GUESS).build();
    ORACLE_PIN = FixtureLoader.readOracleVersion(FIXTURES_ROOT);
    // D-07: log the pin on first test output so every CI run records it.
    System.out.println("=== Magika Parity Harness ===");
    System.out.println(ORACLE_PIN);
    System.out.println("Bundled model SHA-256: " + MAGIKA_HIGH.getModelSha256());
    System.out.println("==============================");
  }

  @AfterAll
  static void tearDown() {
    if (MAGIKA_HIGH != null) {
      MAGIKA_HIGH.close();
    }
    if (MAGIKA_MEDIUM != null) {
      MAGIKA_MEDIUM.close();
    }
    if (MAGIKA_BEST != null) {
      MAGIKA_BEST.close();
    }
  }

  /**
   * TEST-04 / TEST-05: one DynamicTest per HIGH-mode fixture via identifyPath. Excludes
   * mode-prefixed fixtures (medium-confidence-*, best-guess-*) so each mode-divergence fixture is
   * exercised exactly once — under its own dedicated factory.
   */
  @TestFactory
  Stream<DynamicTest> parityHighConfidenceFixtures() throws IOException {
    List<Path> all = FixtureLoader.discoverFixtures(FIXTURES_ROOT);
    assertThat(all)
      .as("at least 25 fixtures required (TEST-01)")
      .hasSizeGreaterThanOrEqualTo(25);
    return all.stream()
      .filter(p -> {
        String n = p.getFileName().toString();
        return !n.startsWith("medium-confidence-") && !n.startsWith("best-guess-");
      })
      .map(fx -> dynamicTest(
        FIXTURES_ROOT.relativize(fx).toString(),
        () -> assertParity(fx, MAGIKA_HIGH.identifyPath(fx))));
  }

  /** TEST-12 (Plan 02-02): MEDIUM_CONFIDENCE-mode fixtures discovered by filename prefix. */
  @TestFactory
  Stream<DynamicTest> parityMediumConfidenceFixtures() throws IOException {
    return discoverModeFixtures("medium-confidence-")
      .map(fx -> dynamicTest(
        FIXTURES_ROOT.relativize(fx).toString(),
        () -> assertParity(fx, MAGIKA_MEDIUM.identifyPath(fx))));
  }

  /** TEST-13 (Plan 02-02): BEST_GUESS-mode fixtures discovered by filename prefix. */
  @TestFactory
  Stream<DynamicTest> parityBestGuessFixtures() throws IOException {
    return discoverModeFixtures("best-guess-")
      .map(fx -> dynamicTest(
        FIXTURES_ROOT.relativize(fx).toString(),
        () -> assertParity(fx, MAGIKA_BEST.identifyPath(fx))));
  }

  /**
   * Helper: discover fixtures whose filename begins with the given mode prefix. Returns an empty
   * stream when no matches (Wave 0 state — fixtures land in Wave 1).
   */
  private static Stream<Path> discoverModeFixtures(String prefix) throws IOException {
    List<Path> all = FixtureLoader.discoverFixtures(FIXTURES_ROOT);
    return all.stream().filter(p -> p.getFileName().toString().startsWith(prefix));
  }

  /** TEST-08: at least one fixture via {@code identifyBytes}. */
  @Test
  void identifyBytesExercised() throws IOException {
    Path txt = FIXTURES_ROOT.resolve("text/sample.md");
    byte[] bytes = Files.readAllBytes(txt);
    MagikaResult r = MAGIKA_HIGH.identifyBytes(bytes);
    assertParity(txt, r);
  }

  /**
   * TEST-08 + D-09 regression: ZIP end-of-central-directory is at the file tail.
   * {@code identifyStream} MUST buffer all three slices (beg/mid/end); a {@code beg_size}-only
   * implementation silently returns the wrong label for this input.
   */
  @Test
  void identifyStreamExercised_zip_tail_signal() throws IOException {
    Path zip = FIXTURES_ROOT.resolve("archives/sample.zip");
    try (InputStream in = Files.newInputStream(zip)) {
      MagikaResult r = MAGIKA_HIGH.identifyStream(in);
      assertParity(zip, r);
    }
  }

  /** Second D-09 regression: PDF trailer lives in the last ~100 bytes of the file. */
  @Test
  void identifyStreamExercised_pdf_tail_signal() throws IOException {
    Path pdf = FIXTURES_ROOT.resolve("documents/sample.pdf");
    try (InputStream in = Files.newInputStream(pdf)) {
      MagikaResult r = MAGIKA_HIGH.identifyStream(in);
      assertParity(pdf, r);
    }
  }

  /**
   * CR-01 regression: {@code identifyStream} on a 0-byte stream MUST hit the small-file branch
   * (EMPTY sentinel) instead of running the model on all-padding tokens.
   */
  @Test
  void identifyStreamExercised_empty_stream_small_file_branch() throws IOException {
    Path empty = FIXTURES_ROOT.resolve("edge/stream-empty.bin");
    try (InputStream in = Files.newInputStream(empty)) {
      MagikaResult r = MAGIKA_HIGH.identifyStream(in);
      assertParity(empty, r);
    }
  }

  /**
   * CR-01 regression: {@code identifyStream} on a 1-byte UTF-8-valid stream MUST return the
   * UNDEFINED+TXT small-file sentinel (N &lt; min_file_size_for_dl, decode succeeds).
   */
  @Test
  void identifyStreamExercised_one_text_byte_small_file_branch() throws IOException {
    Path oneByte = FIXTURES_ROOT.resolve("edge/stream-one-text-byte.txt");
    try (InputStream in = Files.newInputStream(oneByte)) {
      MagikaResult r = MAGIKA_HIGH.identifyStream(in);
      assertParity(oneByte, r);
    }
  }

  /**
   * CR-01 regression: {@code identifyStream} on a 7-byte non-UTF-8 stream MUST return the
   * UNDEFINED+UNKNOWN small-file sentinel (N &lt; min_file_size_for_dl, decode fails).
   */
  @Test
  void identifyStreamExercised_seven_invalid_utf8_bytes_small_file_branch() throws IOException {
    Path seven = FIXTURES_ROOT.resolve("edge/stream-seven-bytes.bin");
    try (InputStream in = Files.newInputStream(seven)) {
      MagikaResult r = MAGIKA_HIGH.identifyStream(in);
      assertParity(seven, r);
    }
  }

  /**
   * CR-02 regression: a path with N &gt;= min_file_size_for_dl whose leading whitespace strips the
   * beg window down to fewer than min_file_size_for_dl real tokens MUST hit the post-token
   * "stripped content too short" branch (algorithm-notes §"Small-file branches" row 3) — model is
   * not invoked, dl=UNDEFINED, output=TXT (raw unstripped leading block decodes as valid UTF-8).
   */
  @Test
  void identifyPathExercised_leading_whitespace_post_token_short_branch() throws IOException {
    Path fixture = FIXTURES_ROOT.resolve("edge/path-leading-whitespace.txt");
    MagikaResult r = MAGIKA_HIGH.identifyPath(fixture);
    assertParity(fixture, r);
  }

  /**
   * The parity comparator — strict equality on both labels, both overwriteReason fields, and
   * status; {@code 1e-4} tolerance on score. Failure messages carry enough context to debug
   * without re-reading the fixture or sidecar.
   *
   * <p>TEST-11 / Plan 02-02: {@code reasonOk} (output side) and {@code dlReasonOk} (dl side, the
   * by-construction NONE invariant) are strict-equality checks. Without {@code reasonOk}, a
   * {@code randomtxt → txt} OVERWRITE_MAP fixture is indistinguishable from native txt content;
   * making the reason load-bearing is what gives TEST-11 its coverage value.
   *
   * <p>REF-01 / Plan 03-01: {@code statusOk} is a strict-equality check on the new status field.
   * All 35 v0.2 sidecars carry {@code status="ok"} per A-04; non-OK Status values are exercised
   * only by {@code BatchIdentifyIT} (Java-only synthetic-error tests, no oracle counterpart).
   */
  private static void assertParity(Path fixture, MagikaResult actual) throws IOException {
    ExpectedResult expected = FixtureLoader.loadExpected(fixture);

    String actDlLabel = actual.dl().label().label();
    String actOutLabel = actual.output().label().label();
    double actScore = actual.score();

    boolean dlOk = expected.dl().label().equals(actDlLabel);
    boolean outOk = expected.output().label().equals(actOutLabel);
    boolean scoreOk = Math.abs(expected.score() - actScore) < SCORE_TOLERANCE;

    // TEST-11 / Plan 02-02: strict equality on overwriteReason — output side (load-bearing for
    // TEST-11 OVERWRITE_MAP coverage) and dl side (by-construction NONE invariant per
    // fixtures/README.md). Both fields are already in every v0.1 sidecar; tightening surfaces
    // any latent reason drift loudly per CLAUDE.md non-negotiable.
    String expectedOutReason = expected.output().overwriteReason();
    String actualOutReason = actual.output().overwriteReason().name();
    boolean reasonOk = expectedOutReason.equals(actualOutReason);

    String expectedDlReason = expected.dl().overwriteReason();
    String actualDlReason = actual.dl().overwriteReason().name();
    boolean dlReasonOk = expectedDlReason.equals(actualDlReason);

    // REF-01 / A-04: strict Status equality. All 35 v0.2 sidecars carry status="ok"; non-OK
    // values appear only in BatchIdentifyIT (Java-only synthetic-error tests; no oracle counterpart).
    String expectedStatus = expected.status();
    String actualStatus = actual.status().name().toLowerCase(java.util.Locale.ROOT);
    boolean statusOk = expectedStatus.equals(actualStatus);

    if (dlOk && outOk && scoreOk && reasonOk && dlReasonOk && statusOk) {
      return;
    }

    // TEST-05 / TEST-08 human-readable diff with reproducer. The "[reason mismatch]" and
    // "[status mismatch]" banners make drift visible at a glance without parsing the lines below.
    String reasonBanner = (reasonOk && dlReasonOk) ? "" : " [reason mismatch]";
    String statusBanner = statusOk ? "" : " [status mismatch]";
    String msg = String.format(
      "%nParity disagreement:%s%s%n"
        + "  fixture:    %s%n"
        + "  expected:   dl=%s (%.6f, %s), output=%s (%.6f, %s), status=%s%n"
        + "  actual:     dl=%s (%.6f, %s), output=%s (%.6f, %s), status=%s%n"
        + "  tolerance:  |delta| < %.0e (score delta = %.6e)%n"
        + "  oracle:     magika==%s @ upstream SHA %s%n"
        + "  reproducer: Magika m = Magika.create(); m.identifyPath(Path.of(\"%s\"));",
      reasonBanner, statusBanner,
      FIXTURES_ROOT.relativize(fixture),
      expected.dl().label(), expected.dl().score(), expectedDlReason,
      expected.output().label(), expected.output().score(), expectedOutReason,
      expectedStatus,
      actDlLabel, actual.dl().score(), actualDlReason,
      actOutLabel, actScore, actualOutReason,
      actualStatus,
      SCORE_TOLERANCE, expected.score() - actScore,
      expected.upstreamMagikaVersion(), expected.upstreamMagikaGitSha(),
      fixture.toAbsolutePath().normalize());

    throw new AssertionError(msg);
  }
}
