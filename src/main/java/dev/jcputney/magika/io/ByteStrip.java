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
 * Python {@code bytes.lstrip()} / {@code bytes.rstrip()} parity helper (IO-03, ALG-03).
 *
 * <p>Strips EXACTLY the six bytes CPython {@code bytes} object treats as whitespace by default:
 * {@code 0x09} (HT), {@code 0x0A} (LF), {@code 0x0B} (VT), {@code 0x0C} (FF), {@code 0x0D} (CR),
 * {@code 0x20} (SP). Does NOT strip NBSP ({@code 0xA0}), NEL ({@code 0x85}), nor any Unicode
 * whitespace category. Cite: see {@code docs/algorithm-notes.md} §"lstrip byte set" for the
 * upstream CPython reference.
 *
 * <p>Index-based: returns the count of bytes to skip rather than copying a new array. Callers
 * slice themselves. This avoids allocation on the hot window-extraction path and preserves IO-04
 * (no String conversion during strip).
 */
final class ByteStrip {

  /** Lookup table for the six Python-strip bytes; other 250 positions are false. */
  private static final boolean[] STRIP_SET = new boolean[256];

  static {
    STRIP_SET[0x09] = true;
    STRIP_SET[0x0A] = true;
    STRIP_SET[0x0B] = true;
    STRIP_SET[0x0C] = true;
    STRIP_SET[0x0D] = true;
    STRIP_SET[0x20] = true;
  }

  private ByteStrip() {
    // utility class
  }

  /**
   * Count bytes at {@code raw[offset..offset+length)} that are in the strip set, scanning from the
   * start. Return value is the number of leading strip-set bytes — caller slices
   * {@code raw[offset + n .. offset + length)}.
   */
  static int leadingStripCount(byte[] raw, int offset, int length) {
    int n = 0;
    while (n < length && STRIP_SET[raw[offset + n] & 0xFF]) {
      n++;
    }
    return n;
  }

  /**
   * Count bytes at {@code raw[offset..offset+length)} that are in the strip set, scanning from the
   * end. Return value is the number of trailing strip-set bytes — caller slices
   * {@code raw[offset .. offset + length - n)}.
   */
  static int trailingStripCount(byte[] raw, int offset, int length) {
    int n = 0;
    while (n < length && STRIP_SET[raw[offset + length - 1 - n] & 0xFF]) {
      n++;
    }
    return n;
  }
}
