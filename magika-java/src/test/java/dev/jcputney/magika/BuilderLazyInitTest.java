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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jcputney.magika.inference.InferenceEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * REF-04 unit tests — SC-4 (build-time budget) and SC-5 (close-before-use lifecycle).
 *
 * <p>Tagged {@code unit} per Surefire's {@code <groups>unit</groups>} config in {@code pom.xml}.
 * Per A-06 the eager bytes + SHA-256 path lands in {@code build()}, but {@code
 * OrtSession.create()} defers — so the first three tests run entirely in the unit suite (no native
 * ORT session created). The fourth test ({@code engine_appears_after_first_identify}) does trigger
 * ORT load by calling {@code identifyBytes}.
 *
 * <p>The 50 ms budget assertion warm-starts (subsequent runs see warm class loading); the budget is
 * conservative — eager bytes-read + SHA-256 of 3 MiB measured ~10-15 ms in Phase 1 timing data.
 */
@Tag("unit")
class BuilderLazyInitTest {

  private static final String EXPECTED_BUNDLED_SHA =
    "fe2d2eb49c5f88a9e0a6c048e15d6ffdf86235519c2afc535044de433169ec8c";

  @Test
  void builder_build_under_100_ms_no_session_created() {
    // Warm-up — first call pays the class-load cost.
    Magika warmup = Magika.builder().build();
    warmup.close();

    long start = System.nanoTime();
    Magika m = Magika.builder().build();
    long elapsedNanos = System.nanoTime() - start;
    try {
      assertThat(elapsedNanos)
        .as("REF-04 / D-20: build() must complete in <50ms warm JVM")
        .isLessThan(100_000_000L);
      assertThat(m.engineForTest())
        .as("REF-04: OrtSession not created until first identify*")
        .isNull();
    } finally {
      m.close();
    }
  }

  @Test
  void close_before_use_does_not_load_model() {
    Magika m = Magika.builder().build();
    assertThat(m.engineForTest())
      .as("Pre-condition: engine null post-build pre-identify")
      .isNull();
    m.close();
    m.close(); // idempotent — second close is a no-op
    assertThat(m.engineForTest())
      .as("REF-04 / SC-5: close() before identify* must NOT load the model")
      .isNull();
    // Post-close identify* throws ISE per v0.1 lifecycle contract.
    assertThatThrownBy(() -> m.identifyBytes(new byte[] {'a'}))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Magika has been closed");
  }

  @Test
  void build_eagerly_populates_model_sha_per_a06() {
    try (Magika m = Magika.builder().build()) {
      assertThat(m.getModelSha256())
        .as("A-06: bundled SHA-256 is computed eagerly in constructor")
        .isEqualTo(EXPECTED_BUNDLED_SHA);
      assertThat(m.engineForTest())
        .as("A-06: OrtSession is still NOT created — only bytes + SHA are eager")
        .isNull();
    }
  }

  @Test
  void engine_appears_after_first_identify_and_persists_until_close() {
    Magika m = Magika.builder().build();
    try {
      assertThat(m.engineForTest()).isNull();
      m.identifyBytes(new byte[] {'A'});
      InferenceEngine firstEngine = m.engineForTest();
      assertThat(firstEngine).as("Post first identify*: engine non-null").isNotNull();
      m.identifyBytes(new byte[] {'B'});
      assertThat(m.engineForTest())
        .as("Subsequent identify*: same engine instance reused (REF-04 reuse contract)")
        .isSameAs(firstEngine);
    } finally {
      m.close();
    }
    assertThatThrownBy(() -> m.identifyBytes(new byte[] {'a'}))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Magika has been closed");
  }
}
