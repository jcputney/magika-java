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

package dev.jcputney.magika;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * REF-02 SC-2 — batch {@link Magika#identifyPaths} integration tests. Asserts:
 *
 * <ol>
 * <li>ordering: {@code result.get(i)} corresponds to {@code input.get(i)}.
 * <li>Status mapping per A-02: {@link java.nio.file.NoSuchFileException} →
 * {@link Status#FILE_NOT_FOUND_ERROR}, {@link java.nio.file.AccessDeniedException} →
 * {@link Status#PERMISSION_ERROR}, others → {@link Status#UNKNOWN}.
 * <li>null elements in input throw NPE with no partial results (D-16).
 * <li>{@link Error} and {@link IllegalStateException} propagate (D-15) — verified by post-close
 * ISE.
 * </ol>
 *
 * <p>Tagged {@code parity} per Failsafe's {@code <groups>parity</groups>} config.
 */
@Tag("parity")
class BatchIdentifyIT {

  private static final Path FIXTURES_ROOT = Paths.get("src/test/resources/fixtures");

  @Test
  void identifyPaths_preserves_input_order_for_mixed_valid_inputs() {
    Path png = FIXTURES_ROOT.resolve("images/sample.png");
    Path zip = FIXTURES_ROOT.resolve("archives/sample.zip");
    Path pdf = FIXTURES_ROOT.resolve("documents/sample.pdf");
    try (Magika m = Magika.create()) {
      List<MagikaResult> results = m.identifyPaths(List.of(png, zip, pdf));
      assertThat(results).hasSize(3);
      assertThat(results.get(0).status()).isEqualTo(Status.OK);
      assertThat(results.get(0).output().type().label()).isEqualTo("png");
      assertThat(results.get(1).status()).isEqualTo(Status.OK);
      assertThat(results.get(1).output().type().label()).isEqualTo("zip");
      assertThat(results.get(2).status()).isEqualTo(Status.OK);
      assertThat(results.get(2).output().type().label()).isEqualTo("pdf");
    }
  }

  @Test
  void identifyPaths_missing_file_returns_FILE_NOT_FOUND_ERROR_status() {
    Path png = FIXTURES_ROOT.resolve("images/sample.png");
    Path missing = FIXTURES_ROOT.resolve("does-not-exist.bin");
    try (Magika m = Magika.create()) {
      List<MagikaResult> results = m.identifyPaths(List.of(png, missing));
      assertThat(results).hasSize(2);
      assertThat(results.get(0).status()).isEqualTo(Status.OK);
      assertThat(results.get(0).output().type().label()).isEqualTo("png");
      MagikaResult miss = results.get(1);
      assertThat(miss.status()).isEqualTo(Status.FILE_NOT_FOUND_ERROR);
      assertThat(miss.dl().type().label()).isEqualTo("undefined");
      assertThat(miss.output().type().label()).isEqualTo("unknown");
      assertThat(miss.score()).isEqualTo(0.0);
    }
  }

  @Test
  void identifyPaths_unreadable_file_returns_PERMISSION_ERROR_status(@TempDir
  Path tmp)
    throws Exception {
    // POSIX-only — Windows chmod 000 does not reliably make a file unreadable to the JVM.
    assumeTrue(
      Files.getFileStore(tmp).supportsFileAttributeView("posix"),
      "PERMISSION_ERROR test requires POSIX file attributes");

    Path unreadable = tmp.resolve("locked.bin");
    Files.write(unreadable, new byte[] {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'});
    Set<PosixFilePermission> noPerms = EnumSet.noneOf(PosixFilePermission.class);
    Files.setPosixFilePermissions(unreadable, noPerms);
    try (Magika m = Magika.create()) {
      List<MagikaResult> results = m.identifyPaths(List.of(unreadable));
      assertThat(results).hasSize(1);
      MagikaResult r = results.get(0);
      assertThat(r.status()).isEqualTo(Status.PERMISSION_ERROR);
      assertThat(r.dl().type().label()).isEqualTo("undefined");
      assertThat(r.output().type().label()).isEqualTo("unknown");
      assertThat(r.score()).isEqualTo(0.0);
    } finally {
      // restore perms so @TempDir cleanup works
      Files.setPosixFilePermissions(
        unreadable,
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }
  }

  @Test
  void identifyPaths_null_element_throws_NPE_with_no_partial_results() {
    Path png = FIXTURES_ROOT.resolve("images/sample.png");
    try (Magika m = Magika.create()) {
      assertThatThrownBy(() -> m.identifyPaths(Arrays.asList(png, null, png)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("paths[1]"); // D-16 — index-aware message
    }
  }

  @Test
  void identifyPaths_null_list_throws_NPE() {
    try (Magika m = Magika.create()) {
      assertThatThrownBy(() -> m.identifyPaths(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("paths");
    }
  }

  @Test
  void identifyPaths_post_close_throws_IllegalStateException() {
    Magika m = Magika.create();
    m.close();
    Path png = FIXTURES_ROOT.resolve("images/sample.png");
    assertThatThrownBy(() -> m.identifyPaths(List.of(png)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Magika has been closed");
  }

  @Test
  void identifyPaths_parallelism_one_returns_same_results_as_default() {
    Path png = FIXTURES_ROOT.resolve("images/sample.png");
    Path zip = FIXTURES_ROOT.resolve("archives/sample.zip");
    try (Magika m = Magika.create()) {
      List<MagikaResult> serial = m.identifyPaths(List.of(png, zip), 1);
      assertThat(serial).hasSize(2);
      assertThat(serial.get(0).status()).isEqualTo(Status.OK);
      assertThat(serial.get(0).output().type().label()).isEqualTo("png");
      assertThat(serial.get(1).status()).isEqualTo(Status.OK);
      assertThat(serial.get(1).output().type().label()).isEqualTo("zip");
    }
  }

  @Test
  void identifyPaths_parallelism_zero_throws_IllegalArgumentException() {
    Path png = FIXTURES_ROOT.resolve("images/sample.png");
    try (Magika m = Magika.create()) {
      assertThatThrownBy(() -> m.identifyPaths(List.of(png), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(">= 1");
    }
  }
}
