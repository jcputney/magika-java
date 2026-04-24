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

package dev.jcputney.magika.inference;

import java.util.List;
import java.util.Map;

/**
 * Test-scope {@link InferenceEngine} that returns canned probabilities without loading ONNX
 * (INF-02). Lets every postprocess + window-extractor test run with ZERO {@code ai.onnxruntime.*}
 * on the classpath.
 *
 * <p>Two factory methods:
 *
 * <ul>
 * <li>{@link #withArgmax(String, double, List, int)} — concentrates probability mass on a single
 * label.
 * <li>{@link #withLabelScores(Map, List)} — per-label scores; unmapped labels get 0.0.
 * </ul>
 *
 * <p>{@link #run(int[])} returns a defensive copy of the probability vector. Calling
 * {@link #close()} flips a flag; a post-close {@code run} throws {@link IllegalStateException}.
 */
public final class FakeInferenceEngine implements InferenceEngine {

  private final float[] probabilities;
  private final String topContentTypeLabel;
  private final double topScore;
  private boolean closed;

  private FakeInferenceEngine(float[] probabilities, String topContentTypeLabel, double topScore) {
    this.probabilities = probabilities;
    this.topContentTypeLabel = topContentTypeLabel;
    this.topScore = topScore;
  }

  /**
   * Build a fake engine whose softmax output spikes at {@code argmaxIndex} with {@code score} and
   * distributes the remaining probability uniformly across the other positions.
   */
  public static FakeInferenceEngine withArgmax(
    String label, double score, List<String> labelsSpace, int argmaxIndex) {
    if (argmaxIndex < 0 || argmaxIndex >= labelsSpace.size()) {
      throw new IllegalArgumentException(
        "argmaxIndex out of range: " + argmaxIndex + " / " + labelsSpace.size());
    }
    int n = labelsSpace.size();
    float[] probs = new float[n];
    double rest = Math.max(0.0, 1.0 - score) / Math.max(1, n - 1);
    for (int i = 0; i < n; i++) {
      probs[i] = (float) (i == argmaxIndex ? score : rest);
    }
    return new FakeInferenceEngine(probs, label, score);
  }

  /**
   * Build a fake engine from a per-label score map. Labels missing from {@code scores} get
   * probability 0.0. The label with the highest score is the {@code topContentTypeLabel}.
   */
  public static FakeInferenceEngine withLabelScores(
    Map<String, Double> scores, List<String> labelsSpace) {
    int n = labelsSpace.size();
    float[] probs = new float[n];
    String topLabel = labelsSpace.isEmpty() ? "" : labelsSpace.get(0);
    double topScore = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < n; i++) {
      String label = labelsSpace.get(i);
      double s = scores.getOrDefault(label, 0.0);
      probs[i] = (float) s;
      if (s > topScore) {
        topScore = s;
        topLabel = label;
      }
    }
    if (topScore == Double.NEGATIVE_INFINITY) {
      topScore = 0.0;
    }
    return new FakeInferenceEngine(probs, topLabel, topScore);
  }

  @Override
  public InferenceResult run(int[] tokens) {
    if (closed) {
      throw new IllegalStateException("FakeInferenceEngine has been closed");
    }
    return new InferenceResult(probabilities.clone(), topContentTypeLabel, topScore);
  }

  @Override
  public void close() {
    closed = true;
  }
}
