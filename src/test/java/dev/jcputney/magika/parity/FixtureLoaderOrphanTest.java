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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * IN-05 unit tests: {@link FixtureLoader#discoverFixtures(Path)} skips orphaned fixture files
 * (bytes without a {@code .expected.json} sidecar) but does not abort discovery. The orphan-skip
 * count is the observable contract; the WARN log added in IN-05 is a diagnostic aid.
 */
@Tag("unit")
class FixtureLoaderOrphanTest {

  @Test
  void discoverFixtures_skips_orphan_and_keeps_paired(@TempDir Path tmp) throws IOException {
    Path fxRoot = Files.createDirectories(tmp.resolve("fixtures"));
    // Orphan: bytes only, no sidecar.
    Files.writeString(fxRoot.resolve("orphan.bin"), "");
    // Paired: bytes + sidecar.
    Files.writeString(fxRoot.resolve("paired.bin"), "");
    Files.writeString(fxRoot.resolve("paired.bin.expected.json"), "{}");

    List<Path> found = FixtureLoader.discoverFixtures(fxRoot);

    assertThat(found).hasSize(1);
    assertThat(found.get(0).getFileName().toString()).isEqualTo("paired.bin");
  }

  @Test
  void discoverFixtures_excludes_README_and_ORACLE_VERSION(@TempDir Path tmp) throws IOException {
    Path fxRoot = Files.createDirectories(tmp.resolve("fixtures"));
    Files.writeString(fxRoot.resolve("README.md"), "docs");
    Files.writeString(fxRoot.resolve("ORACLE_VERSION"), "v1");
    Files.writeString(fxRoot.resolve("real.bin"), "");
    Files.writeString(fxRoot.resolve("real.bin.expected.json"), "{}");

    List<Path> found = FixtureLoader.discoverFixtures(fxRoot);
    assertThat(found).hasSize(1);
  }
}
