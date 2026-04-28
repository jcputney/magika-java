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

package dev.jcputney.magika.perf;

import dev.jcputney.magika.Magika;
import dev.jcputney.magika.MagikaResult;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Steady-state {@code identifyBytes} latency for four representative input shapes:
 *
 * <ul>
 * <li><b>sub-threshold</b> (synthetic 1-byte buffer) — hits the small-file short-circuit
 * branch ({@code size < min_file_size_for_dl}, currently 8 B); no model inference, no
 * tensor build. The cleanest measurement of "how fast can we say UNDEFINED?".
 * <li><b>small</b> ({@code images/sample.gif}, 35 B) — above the 8 B threshold so it
 * runs the full pipeline despite being tiny; useful as a "smallest realistic file" data
 * point.
 * <li><b>medium</b> ({@code code/sample.java}, 593 B) — full pipeline, single-window input.
 * <li><b>large</b> ({@code archives/sample.tar}, 10 KB) — full pipeline, multi-window input.
 * </ul>
 *
 * <p>JIT and ORT-session lazy init are absorbed during JMH's warmup phase. The reported number is
 * the steady-state per-call cost on a hot {@link Magika} instance — the production-relevant
 * measurement, since real consumers hold a long-lived instance per the v0.1 lifecycle contract.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class IdentifyBytesBenchmark {

  /**
   * Marker prefix that selects a synthetic byte array instead of a classpath fixture. Lets us
   * cleanly exercise the small-file short-circuit branch without committing a sub-8-byte fixture
   * file (any test-resource of that size triggers parity-suite expectations we don't want here).
   */
  private static final String SYNTHETIC_1B = "synthetic:1B-sub-threshold";

  @Param({SYNTHETIC_1B, "images/sample.gif", "code/sample.java", "archives/sample.tar"})
  public String fixture;

  private Magika magika;
  private byte[] sample;

  @Setup
  public void setup() throws IOException {
    magika = Magika.builder().build();
    sample = SYNTHETIC_1B.equals(fixture) ? new byte[] {(byte) 'a'} : FixtureLoader.loadBytes(fixture);
  }

  @TearDown
  public void tearDown() {
    magika.close();
  }

  @Benchmark
  public MagikaResult identifyBytes() {
    return magika.identifyBytes(sample);
  }
}
