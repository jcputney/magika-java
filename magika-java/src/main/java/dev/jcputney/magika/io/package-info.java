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
 * Byte-level I/O primitives for Magika: sealed {@code InputSource} hierarchy and
 * the package-private window/strip helpers.
 *
 * <p>Pure byte math. Zero {@code String} conversion during strip/window phase (IO-04).
 * Zero {@code ai.onnxruntime.*} imports. See {@code docs/algorithm-notes.md} §lstrip byte
 * set for the CPython reference this package mirrors.
 */

package dev.jcputney.magika.io;
