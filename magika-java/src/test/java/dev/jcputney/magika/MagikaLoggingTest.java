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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * D-11 logging contract: exactly three log sites in the core on the {@code Magika.class} logger.
 * This test asserts the INFO-on-load event and INFO-on-close event each fire exactly once per
 * {@link Magika} instance.
 *
 * <p>Tagged {@code parity} because the test constructs a real {@link Magika} (which requires the
 * ORT native load). Uses {@code slf4j-simple} as a test-scope binding (see pom.xml) writing to
 * {@code System.err}; the test captures stderr during the identify* call and asserts the log line
 * is present.
 *
 * <p><strong>D-11 (post-WR-01) is exactly three events:</strong>
 *
 * <ul>
 * <li>Load INFO (5 fields: name, version, sha256, contentTypeCount, loadMs) — in {@link
 * Magika#ensureEngine()} (REF-04: deferred from constructor to first identify*).
 * <li>Close INFO (3 fields: name, version, sha256) — in {@link Magika#close()}, fires only on
 * first close (idempotency guard).
 * <li>Error events: ERROR on SHA mismatch in {@link
 * dev.jcputney.magika.inference.onnx.OnnxModelLoader} (covered by {@code
 *       OnnxModelLoaderTest}).
 * </ul>
 *
 * <p>The previous per-call DEBUG-on-overwrite-hit event was removed in 01-07 (WR-01) because it
 * violated D-11's three-event contract and CLAUDE.md's "no per-call logging payloads" rule.
 *
 * <p><strong>REF-04 amendment:</strong> The load INFO event is now fired inside {@code
 * ensureEngine()} — NOT in the constructor. A {@code Magika.create()} call with no subsequent
 * {@code identify*} call would capture zero load events. This test calls {@code
 * identifyBytes(new byte[]{'A'})} before {@code close()} to trigger the load INFO exactly once,
 * preserving the count-of-one assertion.
 */
@Tag("parity")
class MagikaLoggingTest {

  @Test
  void info_log_on_load_and_close_each_fire_once() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    try {
      System.setErr(new PrintStream(buf));
      // slf4j-simple (test-scope) writes INFO+ to stderr on dev.jcputney.magika.Magika logger.
      // REF-04: load INFO fires on first identify*, not on construction — call identifyBytes to
      // trigger it before asserting the count.
      Magika m = Magika.create();
      m.identifyBytes(new byte[] {'A'}); // triggers ensureEngine() → D-11 load INFO fires once
      m.close();
      m.close(); // second close must be a no-op (no second close log)
    } finally {
      System.setErr(originalErr);
    }
    String log = buf.toString();

    // D-11 load: INFO carries name, version, sha256, contentTypeCount, loadMs.
    assertThat(log).contains("Magika loaded:");
    assertThat(log).contains("name=standard_v3_3");
    assertThat(log).contains("version=v3_3");
    assertThat(log).contains("sha256=");
    assertThat(log).contains("contentTypeCount=");
    assertThat(log).contains("loadMs=");

    // D-11 close (WR-01): INFO carries name, version, sha256.
    assertThat(log).contains("Magika closed:");

    // Exactly one occurrence of each event — no duplicates, idempotent close emits zero extra.
    assertThat(countOccurrences(log, "Magika loaded:")).isEqualTo(1);
    assertThat(countOccurrences(log, "Magika closed:")).isEqualTo(1);
  }

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
