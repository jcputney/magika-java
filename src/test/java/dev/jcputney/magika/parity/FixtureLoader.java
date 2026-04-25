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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks the {@code src/test/resources/fixtures/} tree and loads per-fixture sidecar JSONs.
 *
 * <p>A fixture is any regular file NOT named {@code README.md}, {@code ORACLE_VERSION},
 * {@code .gitkeep}, and NOT ending in {@code .expected.json}. Every discovered fixture must have
 * a sibling {@code <name>.<ext>.expected.json}; fixtures without a sidecar are silently filtered
 * out (so a half-added fixture does not break the build).
 */
public final class FixtureLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(FixtureLoader.class);

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();
  private static final String SIDECAR_SUFFIX = ".expected.json";

  private FixtureLoader() {
    // utility class
  }

  /**
   * Returns all fixture files (not sidecars, README, ORACLE_VERSION, or .gitkeep) that have a
   * sibling sidecar JSON, sorted lexicographically by absolute path so the harness order is
   * stable across runs.
   *
   * <p>(DEBT-02 IN-05) Orphan fixtures (bytes without a sibling sidecar) are skipped but logged at
   * SLF4J WARN level so half-added fixtures surface in the developer loop instead of silently
   * dropping out of the suite. The existing count assertion in {@code UpstreamParityIT} catches
   * catastrophic loss; the WARN catches single-fixture drift.
   */
  public static List<Path> discoverFixtures(Path fixturesRoot) throws IOException {
    List<Path> out = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(fixturesRoot)) {
      List<Path> candidates = walk
        .filter(Files::isRegularFile)
        .filter(p -> {
          String n = p.getFileName().toString();
          return !n.endsWith(SIDECAR_SUFFIX)
            && !n.equals("README.md")
            && !n.equals("ORACLE_VERSION")
            && !n.equals(".gitkeep");
        })
        .sorted(Comparator.comparing(Path::toString))
        .toList();
      for (Path p : candidates) {
        Path sidecar = p.resolveSibling(p.getFileName() + SIDECAR_SUFFIX);
        if (Files.exists(sidecar)) {
          out.add(p);
        } else {
          LOGGER.warn("fixture has no sidecar (skipping): fixture={} expected_sidecar={}",
            fixturesRoot.relativize(p), fixturesRoot.relativize(sidecar));
        }
      }
    }
    return out;
  }

  /** Loads the sidecar JSON for the given fixture. */
  public static ExpectedResult loadExpected(Path fixture) throws IOException {
    Path sidecar = fixture.resolveSibling(fixture.getFileName() + SIDECAR_SUFFIX);
    return MAPPER.readValue(sidecar.toFile(), ExpectedResult.class);
  }

  /**
   * Reads the machine-readable oracle pin (D-07) from {@code fixtures/ORACLE_VERSION}. Returns
   * the file contents stripped of trailing whitespace — the two KEY=VALUE lines that the parity
   * harness logs on every test-run start.
   */
  public static String readOracleVersion(Path fixturesRoot) throws IOException {
    Path pin = fixturesRoot.resolve("ORACLE_VERSION");
    return Files.readString(pin).strip();
  }
}
