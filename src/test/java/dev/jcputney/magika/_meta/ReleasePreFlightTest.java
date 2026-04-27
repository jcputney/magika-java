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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Plan 05-03 (D-09a + P-04) pre-flight checklist for the v0.3.0 first Maven Central publish.
 * Asserts CHANGELOG / README / pom shape BEFORE the operator triggers {@code release.yml}.
 *
 * <p><b>Layer (b) of the Phase 5 4-layer validation architecture</b> (see 05-VALIDATION.md +
 * 05-RESEARCH.md §Validation Architecture). Catches operator mistakes (wrong CHANGELOG, missed
 * README placeholder, forgot to bump pom version) before the irreversible Portal publish.
 *
 * <p><b>Skip-when-absent contract (PROC-01 reuse):</b> CHANGELOG.md and README.md may be missing
 * on a fresh clone or shallow CI checkout — those tests use {@link Assumptions#assumeTrue} to
 * report JUnit SKIPPED rather than fail. {@code pom.xml} is always present on a clone (build
 * is broken in a way pre-flight cannot meaningfully report otherwise) — that test uses a HARD
 * assertion with NO {@code Assumptions} guard.
 *
 * <p><b>P-04 — pom version is asserted as {@code 0.3.0-SNAPSHOT}, NOT {@code 0.3.0}:</b>
 * pre-flight runs BEFORE {@code release:prepare} rewrites the version. The pom at this moment
 * carries the SNAPSHOT immediately preceding the planned tag. Asserting on
 * {@code 0.3.0-SNAPSHOT} also catches the failure mode where the operator forgot to bump from
 * {@code 0.2.x-SNAPSHOT} before triggering — which would make {@code release:prepare} tag the
 * wrong version.
 *
 * <p><b>Working-directory assumption (PROC-01 carry-over):</b> {@code Path.of("CHANGELOG.md")}
 * etc. resolves relative to the JVM working directory. Maven Surefire sets
 * {@code basedir == project root} (verified at {@code mvn test} time). If this test is ever run
 * from a subdirectory (IDE runner with non-root cwd, or a future multi-module reorg), the
 * relative paths resolve to the wrong location. Update this Javadoc and the manual probe in
 * 05-VALIDATION.md if that ever changes.
 *
 * <p><b>Verify the skip-contract manually after authoring:</b>
 * <pre>
 * mv CHANGELOG.md CHANGELOG.md.bak \
 *   && mvn -B -ntp test -Dtest=ReleasePreFlightTest \
 *   && mv CHANGELOG.md.bak CHANGELOG.md
 * # Expected: Tests run: 3, Failures: 0, Errors: 0, Skipped: 1
 * </pre>
 */
@Tag("unit")
class ReleasePreFlightTest {

  private static final Pattern RELEASE_SECTION =
      Pattern.compile("(?ms)^## \\[0\\.3\\.0\\].*?\\n(?<body>.+?)(?=^## |\\z)");

  @Test
  void changelog_has_release_section() throws IOException {
    Assumptions.assumeTrue(
        Files.isRegularFile(Path.of("CHANGELOG.md")),
        "CHANGELOG.md absent — pre-flight skipped (likely fresh clone or shallow CI checkout)");

    String changelog = Files.readString(Path.of("CHANGELOG.md"));
    Matcher matcher = RELEASE_SECTION.matcher(changelog);

    assertThat(matcher.find())
        .as("CHANGELOG.md must have a `## [0.3.0]` section before v0.3.0 release")
        .isTrue();
    assertThat(matcher.group("body").strip())
        .as("CHANGELOG.md `## [0.3.0]` section body must not be empty")
        .isNotEmpty();
  }

  @Test
  void readme_install_snippet_filled_with_release_version() throws IOException {
    Assumptions.assumeTrue(
        Files.isRegularFile(Path.of("README.md")),
        "README.md absent — pre-flight skipped");

    String readme = Files.readString(Path.of("README.md"));
    assertThat(readme)
        .as("README.md must reference 0.3.0 in the install snippet (not <!-- pending -->)")
        .contains("0.3.0")
        .doesNotContain("<!-- pending -->");
  }

  @Test
  void pom_version_is_release_snapshot() throws IOException {
    // No Assumptions guard — pom.xml is always present on a clone (per RESEARCH line 595).
    // Absence indicates the build is broken in a way pre-flight cannot meaningfully report.
    String pom = Files.readString(Path.of("pom.xml"));
    // P-04: pre-flight runs BEFORE release:prepare rewrites the version. pom.xml at this
    // moment carries the SNAPSHOT immediately preceding the planned tag. Asserting on
    // `0.3.0-SNAPSHOT` also catches the failure mode where operator forgot to bump from
    // `0.2.x-SNAPSHOT` before triggering — which would make release:prepare tag the wrong
    // version.
    assertThat(pom)
        .as("pom.xml <version> must be 0.3.0-SNAPSHOT before release:prepare")
        .contains("<version>0.3.0-SNAPSHOT</version>");
  }
}
