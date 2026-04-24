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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jcputney.magika.Magika;
import dev.jcputney.magika.MagikaResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Success Criterion #4 — concurrency + lifecycle (API-10, API-11).
 *
 * <p>8 threads × 2 distinct inputs × 100 iterations — catches shared-mutable-state bugs that
 * same-input concurrency tests would miss. 4 threads on PNG, 4 threads on ZIP (per 01-CONTEXT.md
 * Success Criterion #4). Total = 800 identifications; zero label smearing required.
 *
 * <p><strong>Does NOT include concurrent-close</strong> — ORT 1.25.0 provides no guarantee for
 * {@code OrtSession.close()} racing {@code OrtSession.run()}; exercising the race would flake
 * or crash the JVM in CI. The failure mode is documented in {@link Magika#close()} Javadoc.
 *
 * <p>Tagged {@code @Tag("parity")} so Failsafe runs it.
 */
@Tag("parity")
class ConcurrencyIT {

  private static final Path FIXTURES_ROOT = Paths.get("src/test/resources/fixtures");

  @Test
  void shared_instance_two_inputs_eight_threads_zero_smearing() throws Exception {
    Path png = FIXTURES_ROOT.resolve("images/sample.png");
    Path zip = FIXTURES_ROOT.resolve("archives/sample.zip");
    ExpectedResult pngExpected = FixtureLoader.loadExpected(png);
    ExpectedResult zipExpected = FixtureLoader.loadExpected(zip);

    try (Magika m = Magika.create()) {
      byte[] pngBytes = Files.readAllBytes(png);
      byte[] zipBytes = Files.readAllBytes(zip);
      ExecutorService pool = Executors.newFixedThreadPool(8);
      try {
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
          // 4 threads PNG, 4 threads ZIP — interleaved per iteration.
          for (int t = 0; t < 4; t++) {
            results.add(pool.submit(() -> labelOf(m.identifyBytes(pngBytes))));
          }
          for (int t = 0; t < 4; t++) {
            results.add(pool.submit(() -> labelOf(m.identifyBytes(zipBytes))));
          }
        }
        int pngHits = 0;
        int zipHits = 0;
        for (int i = 0; i < results.size(); i++) {
          String actual = results.get(i).get(30, TimeUnit.SECONDS);
          // For each batch of 8 submissions (indices [0..7]), positions 0..3 are PNG
          // and positions 4..7 are ZIP.
          int posInBatch = i % 8;
          if (posInBatch < 4) {
            assertThat(actual)
              .as("PNG result at global index %d (batch pos %d)", i, posInBatch)
              .isEqualTo(pngExpected.output().label());
            pngHits++;
          } else {
            assertThat(actual)
              .as("ZIP result at global index %d (batch pos %d)", i, posInBatch)
              .isEqualTo(zipExpected.output().label());
            zipHits++;
          }
        }
        assertThat(pngHits).as("100 iter * 4 threads = 400 PNG").isEqualTo(400);
        assertThat(zipHits).as("100 iter * 4 threads = 400 ZIP").isEqualTo(400);
      } finally {
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
      }
    }
  }

  /** API-11: calling {@code close()} twice is a no-op, not an exception. */
  @Test
  void idempotent_close() {
    Magika m = Magika.create();
    m.close();
    m.close(); // second close must be a no-op
  }

  /** API-11: post-close identify* throws IllegalStateException with exact message. */
  @Test
  void use_after_close_throws_IllegalStateException() {
    Magika m = Magika.create();
    m.close();
    assertThatThrownBy(() -> m.identifyBytes(new byte[] {'a'}))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Magika has been closed");
  }

  private static String labelOf(MagikaResult r) {
    return r.output().label().label();
  }
}
