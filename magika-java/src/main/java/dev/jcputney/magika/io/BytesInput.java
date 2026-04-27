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

import java.util.Objects;

/**
 * In-memory input (IO-01). Byte array is NOT defensively copied — caller must not mutate after
 * passing. Per D-10, null throws {@code NullPointerException} at construction.
 */
public record BytesInput(byte[] bytes) implements InputSource {

  public BytesInput {
    Objects.requireNonNull(bytes, "bytes");
  }
}
