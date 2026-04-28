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
import java.nio.file.Path;
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
 * Steady-state {@code identifyPath} latency. Mirrors {@link IdentifyBytesBenchmark} with the same
 * three fixtures; the marginal difference vs {@code identifyBytes} is the per-call file read.
 *
 * <p>After the first iteration the OS page cache is warm — measured numbers reflect
 * <i>warm-cache</i> read latency, not cold-disk I/O. Do not use these numbers to characterize
 * disk-bound workloads.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 15, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 15, time = 2, timeUnit = TimeUnit.SECONDS)
public class IdentifyPathBenchmark {

  @Param({"images/sample.gif", "code/sample.java", "archives/sample.tar"})
  public String fixture;

  private Magika magika;
  private Path path;

  @Setup
  public void setup() {
    magika = Magika.builder().build();
    path = FixtureLoader.resolvePath(fixture);
  }

  @TearDown
  public void tearDown() {
    magika.close();
  }

  @Benchmark
  public MagikaResult identifyPath() {
    return magika.identifyPath(path);
  }
}
