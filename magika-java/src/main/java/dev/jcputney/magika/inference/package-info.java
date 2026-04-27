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
/**
 * Inference engine interface and associated records (InferenceInput / InferenceOutput).
 *
 * <p>ArchUnit-enforced (INF-04): zero {@code ai.onnxruntime.*} imports at this level —
 * the ONNX-backed implementation lives exclusively under {@link dev.jcputney.magika.inference.onnx}.
 * This separation lets {@code FakeInferenceEngine} (Plan 3 test double) validate the
 * postprocess pipeline without ONNX on the classpath.
 */

package dev.jcputney.magika.inference;
