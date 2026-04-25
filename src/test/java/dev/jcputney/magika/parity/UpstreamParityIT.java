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

  private static Magika MAGIKA;
  private static String ORACLE_PIN;

  @BeforeAll
  static void setup() throws IOException {
    MAGIKA = Magika.create();
    ORACLE_PIN = FixtureLoader.readOracleVersion(FIXTURES_ROOT);
    // D-07: log the pin on first test output so every CI run records it.
    System.out.println("=== Magika Parity Harness ===");
    System.out.println(ORACLE_PIN);
    System.out.println("Bundled model SHA-256: " + MAGIKA.getModelSha256());
    System.out.println("==============================");
  }

  @AfterAll
  static void tearDown() {
    if (MAGIKA != null) {
      MAGIKA.close();
    }
  }

  /** TEST-04 / TEST-05: one DynamicTest per fixture via identifyPath (the bulk of the harness). */
  @TestFactory
  Stream<DynamicTest> parityEveryFixture() throws IOException {
    List<Path> fixtures = FixtureLoader.discoverFixtures(FIXTURES_ROOT);
    assertThat(fixtures)
      .as("at least 25 fixtures required (TEST-01)")
      .hasSizeGreaterThanOrEqualTo(25);
    return fixtures.stream().map(fx -> dynamicTest(
      FIXTURES_ROOT.relativize(fx).toString(),
      () -> assertParity(fx, MAGIKA.identifyPath(fx))));
  }

  /** TEST-08: at least one fixture via {@code identifyBytes}. */
  @Test
  void identifyBytesExercised() throws IOException {
    Path txt = FIXTURES_ROOT.resolve("text/sample.md");
    byte[] bytes = Files.readAllBytes(txt);
    MagikaResult r = MAGIKA.identifyBytes(bytes);
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
      MagikaResult r = MAGIKA.identifyStream(in);
      assertParity(zip, r);
    }
  }

  /** Second D-09 regression: PDF trailer lives in the last ~100 bytes of the file. */
  @Test
  void identifyStreamExercised_pdf_tail_signal() throws IOException {
    Path pdf = FIXTURES_ROOT.resolve("documents/sample.pdf");
    try (InputStream in = Files.newInputStream(pdf)) {
      MagikaResult r = MAGIKA.identifyStream(in);
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
      MagikaResult r = MAGIKA.identifyStream(in);
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
      MagikaResult r = MAGIKA.identifyStream(in);
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
      MagikaResult r = MAGIKA.identifyStream(in);
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
    MagikaResult r = MAGIKA.identifyPath(fixture);
    assertParity(fixture, r);
  }

  /**
   * The parity comparator — strict equality on both labels, {@code 1e-4} tolerance on score.
   * Failure messages carry enough context to debug without re-reading the fixture or sidecar.
   */
  private static void assertParity(Path fixture, MagikaResult actual) throws IOException {
    ExpectedResult expected = FixtureLoader.loadExpected(fixture);

    String actDlLabel = actual.dl().label().label();
    String actOutLabel = actual.output().label().label();
    double actScore = actual.score();

    boolean dlOk = expected.dl().label().equals(actDlLabel);
    boolean outOk = expected.output().label().equals(actOutLabel);
    boolean scoreOk = Math.abs(expected.score() - actScore) < SCORE_TOLERANCE;

    if (dlOk && outOk && scoreOk) {
      return;
    }

    // TEST-05 / TEST-08 human-readable diff with reproducer.
    String msg = String.format(
      "%nParity disagreement:%n"
        + "  fixture:    %s%n"
        + "  expected:   dl=%s (%.6f), output=%s (%.6f), reason=%s%n"
        + "  actual:     dl=%s (%.6f), output=%s (%.6f), reason=%s%n"
        + "  tolerance:  |delta| < %.0e (score delta = %.6e)%n"
        + "  oracle:     magika==%s @ upstream SHA %s%n"
        + "  reproducer: Magika m = Magika.create(); m.identifyPath(Path.of(\"%s\"));",
      FIXTURES_ROOT.relativize(fixture),
      expected.dl().label(), expected.dl().score(),
      expected.output().label(), expected.output().score(), expected.output().overwriteReason(),
      actDlLabel, actual.dl().score(),
      actOutLabel, actScore, actual.output().overwriteReason(),
      SCORE_TOLERANCE, expected.score() - actScore,
      expected.upstreamMagikaVersion(), expected.upstreamMagikaGitSha(),
      fixture.toAbsolutePath().normalize());

    throw new AssertionError(msg);
  }
}
