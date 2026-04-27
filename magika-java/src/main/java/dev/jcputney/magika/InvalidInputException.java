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
 * Thrown for caller-supplied inputs that are not usable: missing file, path-is-directory, IO error
 * reading the path, etc. Per D-08 and D-10, {@code null} arguments throw
 * {@code NullPointerException} and are NOT wrapped to this type.
 */
public final class InvalidInputException extends MagikaException {

  private static final long serialVersionUID = 1L;

  public InvalidInputException(String message) {
    super(message);
  }

  public InvalidInputException(String message, Throwable cause) {
    super(message, cause);
  }
}
