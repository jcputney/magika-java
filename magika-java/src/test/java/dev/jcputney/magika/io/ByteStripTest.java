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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class ByteStripTest {

  @ParameterizedTest
  @ValueSource(ints = {0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20})
  void leadingStrip_strips_the_six_python_bytes(int b) {
    byte[] raw = new byte[] {(byte) b, 'A'};
    assertThat(ByteStrip.leadingStripCount(raw, 0, raw.length)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20})
  void trailingStrip_strips_the_six_python_bytes(int b) {
    byte[] raw = new byte[] {'A', (byte) b};
    assertThat(ByteStrip.trailingStripCount(raw, 0, raw.length)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {0x00, 0x01, 0x08, 0x0E, 0x1F, 0x21, 0x7F, 0x85, 0xA0, 0xFF})
  void leadingStrip_does_not_strip_non_python_bytes(int b) {
    byte[] raw = new byte[] {(byte) b, 'A'};
    assertThat(ByteStrip.leadingStripCount(raw, 0, raw.length)).isEqualTo(0);
  }

  @ParameterizedTest
  @ValueSource(ints = {0x00, 0x01, 0x08, 0x0E, 0x1F, 0x21, 0x7F, 0x85, 0xA0, 0xFF})
  void trailingStrip_does_not_strip_non_python_bytes(int b) {
    byte[] raw = new byte[] {'A', (byte) b};
    assertThat(ByteStrip.trailingStripCount(raw, 0, raw.length)).isEqualTo(0);
  }

  @Test
  void all_256_byte_values_only_six_strip() {
    int stripped = 0;
    for (int i = 0; i < 256; i++) {
      byte[] raw = new byte[] {(byte) i, 'A'};
      if (ByteStrip.leadingStripCount(raw, 0, raw.length) == 1) {
        stripped++;
      }
    }
    assertThat(stripped)
      .as("Only 6 bytes in Python's bytes.lstrip() default set")
      .isEqualTo(6);
  }

  @Test
  void all_256_byte_values_only_six_trailing_strip() {
    int stripped = 0;
    for (int i = 0; i < 256; i++) {
      byte[] raw = new byte[] {'A', (byte) i};
      if (ByteStrip.trailingStripCount(raw, 0, raw.length) == 1) {
        stripped++;
      }
    }
    assertThat(stripped)
      .as("Only 6 bytes in Python's bytes.rstrip() default set")
      .isEqualTo(6);
  }

  @Test
  void leadingStrip_multiple_prefix_bytes() {
    byte[] raw = new byte[] {0x09, 0x0A, 0x20, 'A', 'B'};
    assertThat(ByteStrip.leadingStripCount(raw, 0, raw.length)).isEqualTo(3);
  }

  @Test
  void trailingStrip_multiple_suffix_bytes() {
    byte[] raw = new byte[] {'A', 'B', 0x09, 0x0A, 0x20};
    assertThat(ByteStrip.trailingStripCount(raw, 0, raw.length)).isEqualTo(3);
  }

  @Test
  void all_strip_bytes_returns_length() {
    byte[] raw = new byte[] {0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20};
    assertThat(ByteStrip.leadingStripCount(raw, 0, raw.length)).isEqualTo(6);
    assertThat(ByteStrip.trailingStripCount(raw, 0, raw.length)).isEqualTo(6);
  }

  @Test
  void empty_input_returns_zero() {
    byte[] raw = new byte[0];
    assertThat(ByteStrip.leadingStripCount(raw, 0, 0)).isZero();
    assertThat(ByteStrip.trailingStripCount(raw, 0, 0)).isZero();
  }

  @Test
  void high_bit_bytes_do_not_throw_array_index_out_of_bounds() {
    // Regression: byte in Java is signed. Without `& 0xFF` promotion, the high-bit
    // bytes (0x80..0xFF) produce negative array indices and throw AIOOBE. This test
    // exercises the pitfall-3 preventer directly.
    byte[] raw = new byte[] {(byte) 0x80, (byte) 0xFF, (byte) 0xA0, (byte) 0x85};
    assertThat(ByteStrip.leadingStripCount(raw, 0, raw.length)).isZero();
    assertThat(ByteStrip.trailingStripCount(raw, 0, raw.length)).isZero();
  }
}
