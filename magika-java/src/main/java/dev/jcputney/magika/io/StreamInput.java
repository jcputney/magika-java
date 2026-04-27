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

import java.io.InputStream;
import java.util.Objects;

/**
 * Stream input (IO-01). Per D-09, the window extractor buffers enough bytes to cover all three
 * slices (beg, mid, end) — a beg-only read silently breaks parity on tail-signal formats
 * (ZIP/TAR/PDF). Stream is NOT closed by the library; caller owns {@code close()}.
 */
public record StreamInput(InputStream stream) implements InputSource {

  public StreamInput {
    Objects.requireNonNull(stream, "stream");
  }
}
