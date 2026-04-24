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

package dev.jcputney.magika.io;

/**
 * Unifies the three public input kinds {@code byte[] / Path / InputStream} so every identify entry
 * point flows through a single pipeline (IO-01).
 *
 * <p>Sealed — permits exactly {@link BytesInput}, {@link PathInput}, {@link StreamInput}. Do not
 * add new permits without a corresponding public {@code identify*} method on {@code Magika}.
 */
public sealed interface InputSource permits BytesInput, PathInput, StreamInput {
}
