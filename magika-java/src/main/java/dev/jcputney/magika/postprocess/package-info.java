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
 * Decision logic applied to raw model output: overwrite-map, per-type thresholds,
 * low-confidence TXT/UNKNOWN fallback.
 *
 * <p>Zero ONNX imports (POST-06). Zero Jackson imports. Consumes parsed config records
 * from {@link dev.jcputney.magika.config} and the raw label+score from
 * {@link dev.jcputney.magika.inference}. See {@code docs/algorithm-notes.md}
 * §Overwrite-map ordering for the exact Python-parity ordering this package implements.
 */

package dev.jcputney.magika.postprocess;
