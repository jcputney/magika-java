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

/**
 * Root of the Magika runtime-exception hierarchy. All library failures surface as one of the three
 * {@code final} subtypes: {@link ModelLoadException}, {@link InferenceException}, or
 * {@link InvalidInputException}. The underlying {@code ai.onnxruntime.OrtException} is always
 * caught and wrapped; it never leaks to callers (API-09).
 */
public abstract class MagikaException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  protected MagikaException(String message) {
    super(message);
  }

  protected MagikaException(String message, Throwable cause) {
    super(message, cause);
  }
}
