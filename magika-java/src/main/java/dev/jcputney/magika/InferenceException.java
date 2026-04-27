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
 * Thrown when model inference fails at runtime — wraps {@code OrtException} from
 * {@code OrtSession.run()}.
 */
public final class InferenceException extends MagikaException {

  private static final long serialVersionUID = 1L;

  public InferenceException(String message) {
    super(message);
  }

  public InferenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
