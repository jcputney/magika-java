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

package dev.jcputney.magika.inference.onnx;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.TensorInfo;
import dev.jcputney.magika.InferenceException;
import dev.jcputney.magika.ModelLoadException;
import dev.jcputney.magika.inference.InferenceEngine;
import dev.jcputney.magika.inference.InferenceResult;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Real ONNX Runtime 1.25.0 engine (INF-03, INF-05, INF-06, INF-07).
 *
 * <p>The ONLY production class allowed to import {@code ai.onnxruntime.*} (INF-04; enforced at
 * bytecode level by {@code PackageBoundaryTest.onnxRuntimeConfinedToOnnxSubpackage}).
 *
 * <p><strong>FP determinism is pinned</strong> via
 * {@link SessionOptions.OptLevel#BASIC_OPT} and {@code setIntraOpNumThreads(1)} (INF-05) —
 * {@code docs/algorithm-notes.md} §FP determinism. The two options are non-negotiable for
 * Phase 1 because Plan 6's parity harness compares scores within 1e-4 and {@code
 * intraOpNumThreads>1} makes reductions non-deterministic across runs.
 *
 * <p><strong>Input dtype is asserted</strong> as {@link OnnxJavaType#INT32} at session creation
 * via {@link OrtSession#getInputInfo()} (INF-06). If upstream ever ships a model with a different
 * dtype, construction throws {@link ModelLoadException} naming the observed dtype; the loader
 * never silently coerces.
 *
 * <p><strong>Tensor input is built via {@link IntBuffer}</strong> — never {@code byte[]} and
 * never any signed-byte-backed NIO buffer (INF-07, PITFALLS.md §Pitfall 1). The padding token
 * 256 does not fit in a signed byte; {@code (byte) 256 == -1} silently breaks parity on every
 * small-file input.
 *
 * <p><strong>Lifecycle:</strong> one long-lived session per instance. {@link #run(int[])} is
 * thread-safe (ORT {@code OrtSession.run} is thread-safe). {@link #close()} is idempotent and
 * closes the session only; the {@link OrtEnvironment} is a process-wide singleton and MUST NOT
 * be closed here. Post-close {@link #run(int[])} throws {@link IllegalStateException}, not
 * {@link OrtException} (PITFALLS.md §Pitfall 8).
 *
 * <p><strong>Native-lib loading edge cases</strong> (PITFALLS.md §Pitfall 6): construction
 * wraps {@link UnsatisfiedLinkError} and {@link NoClassDefFoundError} into
 * {@link ModelLoadException} with {@code os.name}, {@code os.arch}, and
 * {@code ort=1.25.0} in the message, so a consumer hitting a platform-support gap has
 * actionable info.
 */
public final class OnnxInferenceEngine implements InferenceEngine {

  private static final String ORT_VERSION = "1.25.0";

  private final OrtEnvironment env;
  private final OrtSession session;
  private final String inputName;
  private final int expectedTokens;
  private final String[] labelSpace;

  private volatile boolean closed = false;

  /**
   * Construct from pre-verified model bytes + the configured token width + the model's
   * label-space vocabulary. Callers usually prefer {@link #fromBundledModel(int, List)} which
   * reads and SHA-verifies the bundled bytes first.
   *
   * @param modelBytes     raw {@code .onnx} bytes (ownership transferred; ORT copies internally)
   * @param expectedTokens expected length of the {@code tokens} int-array for each {@link #run}
   *                       call ({@code begSize + midSize + endSize}; 2048 for standard_v3_3)
   * @param labelSpace     the model's softmax output vocabulary (order MUST match
   *                       {@code config.min.json.target_labels_space})
   * @throws ModelLoadException on session creation failure, missing input nodes, non-INT32 input
   *                            dtype, or native-lib load failure
   */
  public OnnxInferenceEngine(byte[] modelBytes, int expectedTokens, List<String> labelSpace) {
    Objects.requireNonNull(modelBytes, "modelBytes");
    Objects.requireNonNull(labelSpace, "labelSpace");
    this.expectedTokens = expectedTokens;
    this.labelSpace = labelSpace.toArray(new String[0]);

    try {
      this.env = OrtEnvironment.getEnvironment();
    } catch (UnsatisfiedLinkError e) {
      throw new ModelLoadException(nativeLoadFailureMessage(), e);
    } catch (NoClassDefFoundError e) {
      throw new ModelLoadException(nativeLoadFailureMessage(), e);
    }

    // SessionOptions is AutoCloseable; close it once the session is created. ORT copies
    // options into the session so the options handle can go away immediately.
    try (SessionOptions opts = new SessionOptions()) {
      opts.setOptimizationLevel(SessionOptions.OptLevel.BASIC_OPT); // INF-05
      opts.setIntraOpNumThreads(1); // INF-05
      this.session = env.createSession(modelBytes, opts);

      Map<String, NodeInfo> inputInfo = this.session.getInputInfo();
      if (inputInfo.isEmpty()) {
        throw new ModelLoadException("bundled model declares no input nodes");
      }
      Iterator<Map.Entry<String, NodeInfo>> it = inputInfo.entrySet().iterator();
      Map.Entry<String, NodeInfo> first = it.next();
      this.inputName = first.getKey();
      TensorInfo ti = (TensorInfo) first.getValue().getInfo();
      if (ti.type != OnnxJavaType.INT32) { // INF-06, Pitfall 1
        throw new ModelLoadException(
          "expected input dtype INT32, got "
            + ti.type
            + " (input name='"
            + this.inputName
            + "')");
      }
    } catch (OrtException e) {
      throw new ModelLoadException(
        "failed to create OrtSession for bundled model: " + nativeLoadFailureMessage(), e);
    } catch (UnsatisfiedLinkError e) {
      throw new ModelLoadException(nativeLoadFailureMessage(), e);
    } catch (NoClassDefFoundError e) {
      throw new ModelLoadException(nativeLoadFailureMessage(), e);
    }
  }

  /**
   * Convenience factory: read and SHA-verify the bundled model bytes via
   * {@link OnnxModelLoader#loadAndVerify()} and construct the engine.
   */
  public static OnnxInferenceEngine fromBundledModel(int expectedTokens, List<String> labelSpace) {
    byte[] bytes = OnnxModelLoader.loadAndVerify();
    return new OnnxInferenceEngine(bytes, expectedTokens, labelSpace);
  }

  @Override
  public InferenceResult run(int[] tokens) {
    if (closed) {
      throw new IllegalStateException("OnnxInferenceEngine has been closed");
    }
    Objects.requireNonNull(tokens, "tokens");
    if (tokens.length != expectedTokens) {
      throw new InferenceException(
        "tokens length " + tokens.length + " != expected " + expectedTokens);
    }

    IntBuffer buf = IntBuffer.allocate(tokens.length); // INF-07 — 32-bit per token so 256 fits
    buf.put(tokens).flip();

    try (OnnxTensor tensor =
      OnnxTensor.createTensor(env, buf, new long[] {1L, tokens.length})) {
      try (OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
        OnnxValue out = result.get(0);
        Object raw = out.getValue();
        if (!(raw instanceof float[][] probs2d)) {
          throw new InferenceException(
            "expected model output shape [1, labels] of float; got " + raw.getClass());
        }
        if (probs2d.length != 1) {
          throw new InferenceException(
            "expected model output batch dimension 1; got " + probs2d.length);
        }
        float[] probs = probs2d[0];
        int argmax = 0;
        for (int i = 1; i < probs.length; i++) {
          if (probs[i] > probs[argmax]) {
            argmax = i;
          }
        }
        // WR-02: hard fail on argmax out of label space rather than silently returning the
        // string literal "undefined". A label-space-length mismatch is a model/config skew
        // (e.g. config.min.json target_labels_space and model.onnx softmax width disagree) —
        // returning a fake "undefined" label hides the skew behind a normal-looking result.
        if (argmax >= labelSpace.length) {
          throw new InferenceException(
            "model output index " + argmax + " exceeds labelSpace length " + labelSpace.length
              + " (model.onnx softmax width vs config.min.json target_labels_space size mismatch)");
        }
        String topLabel = labelSpace[argmax];
        return new InferenceResult(probs, topLabel, probs[argmax]);
      }
    } catch (OrtException e) {
      throw new InferenceException("inference failed", e);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      session.close();
    } catch (OrtException ignored) {
      // Session already dead — idempotent close contract.
    }
    // NOTE: OrtEnvironment is a process-wide singleton; do NOT close it here (Pitfall 8).
  }

  private static String nativeLoadFailureMessage() {
    return "failed to load ONNX Runtime natives (os="
      + System.getProperty("os.name")
      + ", arch="
      + System.getProperty("os.arch")
      + ", ort="
      + ORT_VERSION
      + ")";
  }
}
