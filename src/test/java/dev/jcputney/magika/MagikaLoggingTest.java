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
 * This test asserts the INFO-on-load event fires exactly once per {@link Magika} instance and
 * carries all five expected fields ({@code name}, {@code version}, {@code sha256},
 * {@code contentTypeCount}, {@code loadMs}).
 *
 * <p>Tagged {@code parity} because the test constructs a real {@link Magika} (which requires the
 * ORT native load). Uses {@code slf4j-simple} as a test-scope binding (see pom.xml) writing to
 * {@code System.err}; the test captures stderr during construction and asserts the log line is
 * present.
 *
 * <p>The DEBUG-on-overwrite-hit event is not asserted here because DEBUG is below the default
 * {@code slf4j-simple} log level (INFO) — overwrite hits are rare enough that exercising one
 * reliably is Plan 6 parity-harness territory. The ERROR-on-SHA-mismatch event lives in
 * {@link dev.jcputney.magika.inference.onnx.OnnxModelLoader} and is covered by
 * {@code OnnxModelLoaderTest}.
 */
@Tag("parity")
class MagikaLoggingTest {

  @Test
  void info_log_on_load_fires_once_with_all_five_fields() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    try {
      System.setErr(new PrintStream(buf));
      // slf4j-simple (test-scope) writes INFO+ to stderr on dev.jcputney.magika.Magika logger.
      try (Magika m = Magika.create()) {
        // no-op; just construct so the INFO load event fires.
      }
    } finally {
      System.setErr(originalErr);
    }
    String log = buf.toString();
    // D-11: INFO on load carries name, version, sha256, contentTypeCount, loadMs.
    assertThat(log).contains("Magika loaded:");
    assertThat(log).contains("name=standard_v3_3");
    assertThat(log).contains("version=v3_3");
    assertThat(log).contains("sha256=");
    assertThat(log).contains("contentTypeCount=");
    assertThat(log).contains("loadMs=");

    // Exactly one occurrence — no duplicate INFOs per instance.
    int occurrences = 0;
    int idx = 0;
    while ((idx = log.indexOf("Magika loaded:", idx)) != -1) {
      occurrences++;
      idx++;
    }
    assertThat(occurrences).isEqualTo(1);
  }
}
