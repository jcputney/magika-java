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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Cold first-call latency: measures {@code Magika.builder().build()} + first {@code identifyBytes}
 * + {@code close()} as a single end-to-end operation.
 *
 * <p>Since the v0.2 lazy-init shipped (REF-04), {@link Magika#builder()} returns quickly and the
 * ONNX session creation + model bytes load are deferred until the first {@code identify*()} call.
 * A naive {@code build()}-only benchmark would report ~0 µs and miss any regression in session
 * creation cost. This benchmark forces the lazy init by chaining a real first inference.
 *
 * <p>Run mode is {@link Mode#SingleShotTime}: each iteration is one fresh {@link Magika} instance,
 * timed individually. Iteration count is bumped to 20 (vs 10 for steady-state benchmarks) because
 * single-shot runs have lower per-iteration sample density. Forks bumped to 3 to reduce inter-fork
 * variance on a multi-hundred-millisecond workload.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, warmups = 1)
@Warmup(iterations = 5)
@Measurement(iterations = 20)
public class ColdFirstCallBenchmark {

  private byte[] sample;

  @Setup
  public void setup() throws IOException {
    sample = FixtureLoader.loadBytes("code/sample.java");
  }

  @Benchmark
  public MagikaResult coldFirstCall() {
    try (Magika magika = Magika.builder().build()) {
      return magika.identifyBytes(sample);
    }
  }
}
