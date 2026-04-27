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

import dev.jcputney.magika.inference.InferenceEngine;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * REF-04 SC-6 — concurrent first-use lazy-init invariant.
 *
 * <p>N threads (N = {@code Math.max(2, Runtime.getRuntime().availableProcessors())}) race the
 * first {@code identifyPaths} call on a freshly-built {@code Magika}. Asserts:
 *
 * <ol>
 * <li>identity equality: all threads observe the same {@link InferenceEngine} instance — proves
 * exactly-one OrtSession was created (D-19).
 * <li>log-event count: SLF4J test appender (stderr capture per {@link MagikaLoggingTest})
 * records exactly one D-11 "Magika loaded:" INFO event.
 * <li>all calls succeed: every thread's result carries {@link Status#OK}.
 * </ol>
 *
 * <p>The compose pattern follows {@link dev.jcputney.magika.parity.ConcurrencyIT} for the
 * thread-fanout shape and {@link MagikaLoggingTest} for the stderr capture mechanism.
 *
 * <p>Tagged {@code parity} per Failsafe's {@code <groups>parity</groups>} config.
 */
@Tag("parity")
class ConcurrentLazyInitIT {

  private static final Path FIXTURES_ROOT = Paths.get("src/test/resources/fixtures");

  @Test
  void concurrent_first_use_creates_exactly_one_session_and_one_load_event() throws Exception {
    Path png = FIXTURES_ROOT.resolve("images/sample.png");
    int n = Math.max(2, Runtime.getRuntime().availableProcessors());

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    List<InferenceEngine> observedEngines = Collections.synchronizedList(new ArrayList<>());

    Magika m = null;
    try {
      System.setErr(new PrintStream(buf));
      m = Magika.builder().build();

      // Pre-condition: A-06 / SC-4 — engineForTest() is null post-build pre-identify.
      assertThat(m.engineForTest())
        .as("Pre-condition: engine null post-build pre-identify")
        .isNull();

      ExecutorService pool = Executors.newFixedThreadPool(n);
      try {
        List<Future<MagikaResult>> futures = new ArrayList<>();
        Magika finalMagika = m;
        for (int i = 0; i < n; i++) {
          futures.add(
            pool.submit(
              () -> {
                // Each thread races the first-use path. After the call, capture the engine
                // identity for the SC-6 (a) check.
                List<MagikaResult> results = finalMagika.identifyPaths(List.of(png));
                observedEngines.add(finalMagika.engineForTest());
                return results.get(0);
              }));
        }
        for (Future<MagikaResult> f : futures) {
          MagikaResult r = f.get(60, TimeUnit.SECONDS);
          assertThat(r.status())
            .as("SC-6 (c): every thread's identify call must succeed with status=OK")
            .isEqualTo(Status.OK);
        }
      } finally {
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
      }

      // SC-6 (a): identity equality across all observed engines — proves exactly-one OrtSession.
      InferenceEngine first = observedEngines.get(0);
      assertThat(first).as("Post-fanout: engine non-null").isNotNull();
      for (InferenceEngine seen : observedEngines) {
        assertThat(seen)
          .as(
            "SC-6 (a): all threads must observe the same engine instance"
              + " — exactly one OrtSession created")
          .isSameAs(first);
      }
    } finally {
      System.setErr(originalErr);
      if (m != null) {
        m.close();
      }
    }

    // SC-6 (b): exactly one D-11 load INFO event in the captured stderr.
    String log = buf.toString();
    int loadCount = countOccurrences(log, "Magika loaded:");
    assertThat(loadCount)
      .as(
        "SC-6 (b): exactly one D-11 INFO load event under N-thread first-use;"
          + " captured log was:%n%s",
        log)
      .isEqualTo(1);
  }

  /** countOccurrences — copy verbatim from MagikaLoggingTest.java. */
  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}
