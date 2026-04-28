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

import java.util.Objects;

/**
 * Builder for {@link Magika} (API-01). Phase 1 surface is intentionally minimal — only
 * {@link #predictionMode(PredictionMode)} and {@link #maxBufferBytes(long)} are exposed. Custom
 * model paths, {@code SessionOptions}, and {@code OrtEnvironment} overrides are deferred per the
 * project's anti-feature list.
 *
 * <p><strong>Not thread-safe.</strong> Construct a single builder, call {@link #build()} once, and
 * share the returned {@link Magika} across threads for {@code identify*} calls.
 */
public final class MagikaBuilder {

  private PredictionMode mode = PredictionMode.DEFAULT;
  private long maxBufferBytes = Long.MAX_VALUE;

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
   * Caps the number of bytes the library will buffer when reading from a caller-supplied
   * {@link java.io.InputStream}. Defaults to {@link Long#MAX_VALUE} (unlimited) which preserves
   * upstream-Python parity — every fixture continues to pass byte-for-byte. Configured callers
   * (Tika operators behind upload pipelines, anyone routing untrusted bytes through
   * {@link Magika#identifyStream}) trade an OOM / blocked-thread for an
   * {@link InvalidInputException} when the cap is exceeded.
   *
   * @param bytes positive byte cap, or {@link Long#MAX_VALUE} to disable
   * @throws IllegalArgumentException if {@code bytes < 1}
   */
  public MagikaBuilder maxBufferBytes(long bytes) {
    if (bytes < 1L) {
      throw new IllegalArgumentException("maxBufferBytes must be >= 1, got " + bytes);
    }
    this.maxBufferBytes = bytes;
    return this;
  }

  long maxBufferBytes() {
    return maxBufferBytes;
  }

  /**
   * Builds a new {@link Magika} instance. Expensive (hundreds of ms — loads the ONNX model and
   * creates an {@code OrtSession}). Call once per process and share across threads.
   */
  public Magika build() {
    return new Magika(this);
  }
}
