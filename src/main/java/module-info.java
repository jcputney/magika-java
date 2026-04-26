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
 * REF-03 / Plan 03-03: JPMS module descriptor for {@code dev.jcputney:magika-java}.
 *
 * <p>Single export — only {@code dev.jcputney.magika} (the public API surface) is reachable to
 * consumers (D-03 / D-21). Internal subpackages (config, io, inference, inference.onnx,
 * postprocess, internal) are NOT exported; consumers referencing them will fail at compile time on
 * the module path (verified by src/it/jpms-consumer-negative).
 *
 * <p>No {@code requires transitive} (D-21) — Jackson and ORT are implementation details, not part
 * of the consumer contract.
 */
module dev.jcputney.magika {
  exports dev.jcputney.magika;

  requires com.fasterxml.jackson.databind;
  requires com.microsoft.onnxruntime;
  requires org.slf4j;
}
