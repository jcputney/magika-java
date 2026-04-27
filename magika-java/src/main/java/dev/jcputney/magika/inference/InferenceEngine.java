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

/**
 * Seam between the algorithm pipeline and the ONNX-backed classifier (INF-01). The abstraction
 * lets a test double (Plan 3's {@code FakeInferenceEngine}) drive the postprocess pipeline
 * without ONNX on the classpath.
 *
 * <p><strong>INF-04 constraint:</strong> implementations MUST NOT import
 * {@code ai.onnxruntime.*} except under {@code dev.jcputney.magika.inference.onnx}. The
 * {@code PackageBoundaryTest} enforces this at bytecode level.
 *
 * <p>The {@code tokens} input is the concatenated {@code beg + mid + end} int-array of length
 * {@code begSize + midSize + endSize} — {@code 2048} for {@code standard_v3_3}. Each token is an
 * {@code int} in {@code [0, paddingToken]} where {@code paddingToken == 256} is the sentinel
 * (distinct from any byte value). See {@code docs/algorithm-notes.md} §"Tensor dtype".
 */
public interface InferenceEngine extends AutoCloseable {

  /**
   * Run one inference call on the supplied tokens and return the softmax probabilities + argmax
   * label + top score.
   */
  InferenceResult run(int[] tokens);

  /**
   * Release any native resources (ORT session, environment). Idempotent. No checked exceptions —
   * ORT exceptions are caught and ignored at this seam (ORT must not leak across the interface
   * per INF-04).
   */
  @Override
  void close();
}
