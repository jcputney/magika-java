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
import java.util.ArrayList;
import java.util.List;
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
 * Steady-state {@code identifyPaths(List<Path>)} latency at three batch sizes (1, 10, 100). The
 * input list cycles through the small / medium / large fixtures used by {@link
 * IdentifyBytesBenchmark}, so the per-file cost mix is stable across runs.
 *
 * <p>Reported latency is per <b>batch</b>, not per file. To get amortized per-file cost, divide by
 * {@code batchSize}. Throughput is reported in batches/sec.
 *
 * <p>This benchmark exercises the parallelism contract of {@code identifyPaths}: by default the
 * batch fans out across {@code Runtime.getRuntime().availableProcessors()} threads. The expected
 * scaling shape is sub-linear (per-file cost goes up with batch size as the {@code ForkJoinPool}
 * construction is amortized over more work, then plateaus once the pool saturates).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class IdentifyPathsBatchBenchmark {

  private static final String[] FIXTURES = {
      "images/sample.gif", "code/sample.java", "archives/sample.tar"
  };

  @Param({"1", "10", "100"})
  public int batchSize;

  private Magika magika;
  private List<Path> paths;

  @Setup
  public void setup() {
    magika = Magika.builder().build();
    Path[] resolved = new Path[FIXTURES.length];
    for (int i = 0; i < FIXTURES.length; i++) {
      resolved[i] = FixtureLoader.resolvePath(FIXTURES[i]);
    }
    paths = new ArrayList<>(batchSize);
    for (int i = 0; i < batchSize; i++) {
      paths.add(resolved[i % resolved.length]);
    }
  }

  @TearDown
  public void tearDown() {
    magika.close();
  }

  @Benchmark
  public List<MagikaResult> identifyPaths() {
    return magika.identifyPaths(paths);
  }
}
