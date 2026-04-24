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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Utf8ValidatorTest {

  @Test
  void pure_ascii_is_valid() {
    byte[] raw = "Hello, world!".getBytes(StandardCharsets.US_ASCII);
    assertThat(Utf8Validator.isValid(raw, 0, raw.length)).isTrue();
  }

  @Test
  void valid_multibyte_utf8_is_valid() {
    // "héllo" — é is 0xC3 0xA9, a valid 2-byte UTF-8 sequence.
    byte[] raw = "héllo".getBytes(StandardCharsets.UTF_8);
    assertThat(Utf8Validator.isValid(raw, 0, raw.length)).isTrue();
  }

  @Test
  void lone_high_byte_0xFF_is_invalid() {
    byte[] raw = new byte[] {(byte) 0xFF};
    assertThat(Utf8Validator.isValid(raw, 0, raw.length)).isFalse();
  }

  @Test
  void truncated_multibyte_sequence_is_invalid() {
    // 0xC3 starts a 2-byte sequence but the continuation byte is missing.
    byte[] raw = new byte[] {(byte) 0xC3};
    assertThat(Utf8Validator.isValid(raw, 0, raw.length)).isFalse();
  }

  @Test
  void empty_input_is_valid() {
    byte[] raw = new byte[0];
    assertThat(Utf8Validator.isValid(raw, 0, 0)).isTrue();
  }

  @Test
  void overlong_encoding_C1_81_is_invalid() {
    // 0xC1 0x81 would decode to U+0041 'A' if allowed (overlong). Must reject per RFC 3629.
    byte[] raw = new byte[] {(byte) 0xC1, (byte) 0x81};
    assertThat(Utf8Validator.isValid(raw, 0, raw.length)).isFalse();
  }

  @Test
  void lone_continuation_byte_is_invalid() {
    // 0x80 is a continuation byte without a leading multibyte start.
    byte[] raw = new byte[] {(byte) 0x80};
    assertThat(Utf8Validator.isValid(raw, 0, raw.length)).isFalse();
  }
}
