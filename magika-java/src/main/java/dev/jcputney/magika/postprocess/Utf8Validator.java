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

package dev.jcputney.magika.postprocess;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Strict UTF-8 validation for the small-file branch (POST-04). Parity with Python
 * {@code bytes.decode("utf-8")} (no {@code errors} argument → {@code errors="strict"}).
 *
 * <p><strong>Do NOT use the {@code String(byte[], Charset)} constructor for this check.</strong>
 * That constructor silently substitutes {@code U+FFFD} on invalid sequences and never throws; a
 * Java implementation that uses it will classify every small-file input as TXT, including inputs
 * the Python oracle classifies as UNKNOWN. Cite: {@code docs/algorithm-notes.md} §"UTF-8 decode"
 * and PITFALLS.md §Pitfall 4.
 */
public final class Utf8Validator {

  private Utf8Validator() {
    // utility class
  }

  /**
   * Return {@code true} if {@code bytes[offset..offset+length)} decodes cleanly as UTF-8 in strict
   * mode; {@code false} on {@link CharacterCodingException}. Both {@code onMalformedInput} and
   * {@code onUnmappableCharacter} are explicitly set to {@link CodingErrorAction#REPORT} — this is
   * the default on a fresh {@link CharsetDecoder} but the explicit configuration makes the
   * contract visible in code review and proof-against a later "relaxing" edit.
   */
  public static boolean isValid(byte[] bytes, int offset, int length) {
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      decoder.decode(ByteBuffer.wrap(bytes, offset, length));
      return true;
    } catch (CharacterCodingException e) {
      return false;
    }
  }
}
