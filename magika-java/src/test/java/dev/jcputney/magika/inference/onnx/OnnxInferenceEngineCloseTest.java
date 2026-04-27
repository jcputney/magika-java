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

package dev.jcputney.magika.inference.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jcputney.magika.Magika;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * IN-02 negative log-grep test: a healthy {@link Magika#close()} path emits NO WARN line for
 * {@code OnnxInferenceEngine.close()}. The WARN ONLY fires when {@code OrtSession.close()} throws
 * an {@code OrtException}; on every normal shutdown, no diagnostic should leak into stderr.
 *
 * <p>Uses the same {@code System.setErr} capture pattern as {@code MagikaLoggingTest} — {@code
 * slf4j-simple} (test-scope binding) routes WARN+ to stderr.
 *
 * <p>Tagged {@code unit} so the Wave-0 quick-run command ({@code mvn -q -o test
 * -Dtest='...,MagikaCloseLoggingTest'}) picks it up. The class constructs a real {@link Magika}
 * (which loads ORT natives), so it is functionally a parity-style test even though it lives under
 * the unit tag — keeps the surface light and avoids a separate Failsafe execution.
 */
@Tag("unit")
class OnnxInferenceEngineCloseTest {

  @Test
  void close_after_normal_session_does_not_emit_warn() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    try {
      System.setErr(new PrintStream(buf));
      Magika m = Magika.create();
      m.close();
    } finally {
      System.setErr(originalErr);
    }
    assertThat(buf.toString()).doesNotContain("OrtSession.close() threw");
  }
}
