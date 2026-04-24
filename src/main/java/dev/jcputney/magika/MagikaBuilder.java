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

import dev.jcputney.magika.postprocess.PredictionMode;
import java.util.Objects;

/**
 * Builder for {@link Magika} (API-01). Phase 1 surface is intentionally minimal — only
 * {@link #predictionMode(PredictionMode)} is exposed. Custom model paths, {@code SessionOptions},
 * and {@code OrtEnvironment} overrides are deferred per the project's anti-feature list.
 *
 * <p><strong>Not thread-safe.</strong> Construct a single builder, call {@link #build()} once, and
 * share the returned {@link Magika} across threads for {@code identify*} calls.
 */
public final class MagikaBuilder {

  private PredictionMode mode = PredictionMode.DEFAULT;

  MagikaBuilder() {
    // package-private ctor — callers use Magika.builder()
  }

  /**
   * Sets the {@link PredictionMode}. Default is {@link PredictionMode#HIGH_CONFIDENCE} per upstream
   * {@code magika.py:60} (ALG-06).
   */
  public MagikaBuilder predictionMode(PredictionMode m) {
    this.mode = Objects.requireNonNull(m, "mode");
    return this;
  }

  PredictionMode predictionMode() {
    return mode;
  }

  /**
   * Builds a new {@link Magika} instance. Expensive (hundreds of ms — loads the ONNX model and
   * creates an {@code OrtSession}). Call once per process and share across threads.
   */
  public Magika build() {
    return new Magika(this);
  }
}
