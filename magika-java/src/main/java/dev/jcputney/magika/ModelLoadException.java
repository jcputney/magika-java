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
 * Thrown when the bundled ONNX model, its config JSONs, or the native ONNX Runtime library cannot
 * be loaded. Includes SHA-256 mismatch, dtype mismatch, missing classpath resource, and
 * {@code UnsatisfiedLinkError} wrapping paths.
 */
public final class ModelLoadException extends MagikaException {

  private static final long serialVersionUID = 1L;

  public ModelLoadException(String message) {
    super(message);
  }

  public ModelLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
