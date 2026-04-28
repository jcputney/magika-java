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
import java.io.ByteArrayInputStream;
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
 * Steady-state {@code identifyStream} latency. Each invocation wraps the pre-loaded sample bytes
 * in a fresh {@link ByteArrayInputStream} to isolate the stream-buffering overhead from any disk
 * or network read.
 *
 * <p>Compared to {@link IdentifyBytesBenchmark}, the marginal cost is whatever bounded read +
 * buffering the stream path performs internally before handing the bytes to the inference
 * pipeline. The {@code ByteArrayInputStream} allocation itself is allocation-pressure noise on the
 * young generation; expect the gap to mostly reflect {@code Magika}'s own stream handling.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class IdentifyStreamBenchmark {

  @Param({"images/sample.gif", "code/sample.java", "archives/sample.tar"})
  public String fixture;

  private Magika magika;
  private byte[] sample;

  @Setup
  public void setup() throws IOException {
    magika = Magika.builder().build();
    sample = FixtureLoader.loadBytes(fixture);
  }

  @TearDown
  public void tearDown() {
    magika.close();
  }

  @Benchmark
  public MagikaResult identifyStream() {
    return magika.identifyStream(new ByteArrayInputStream(sample));
  }
}
