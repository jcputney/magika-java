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

import dev.jcputney.magika.config.ThresholdConfig;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ByteWindowExtractorTest {

  private static final ThresholdConfig CFG = new ThresholdConfig(
    1024, 0, 1024, 4096, 256, 8,
    0.5, false,
    List.of("unknown", "empty", "undefined", "txt", "html", "zip"),
    Map.of(),
    Map.of(),
    3);

  @Test
  void bytes_smaller_than_begsize_padded_with_padding_token() throws IOException {
    byte[] input = "abcd".getBytes();
    int[] tokens = ByteWindowExtractor.extract(new BytesInput(input), CFG);

    assertThat(tokens.length).isEqualTo(2048);
    // beg[0..4] = 'a','b','c','d'; beg[4..1024] = paddingToken=256
    assertThat(tokens[0]).isEqualTo('a');
    assertThat(tokens[1]).isEqualTo('b');
    assertThat(tokens[2]).isEqualTo('c');
    assertThat(tokens[3]).isEqualTo('d');
    assertThat(tokens[4]).isEqualTo(256);
    assertThat(tokens[1023]).isEqualTo(256);
    // mid is 0-length for standard_v3_3 (nothing between beg[0..1024) and end[1024..2048))
    // end window: same bytes as beg (N < block_size overlap case), rstripped, last endSize bytes
    // left-padded. With input "abcd" (no trailing strip bytes), end has 4 bytes right-aligned.
    assertThat(tokens[1024]).isEqualTo(256);
    assertThat(tokens[2047]).isEqualTo('d');
    assertThat(tokens[2046]).isEqualTo('c');
    assertThat(tokens[2045]).isEqualTo('b');
    assertThat(tokens[2044]).isEqualTo('a');
  }

  @Test
  void strip_applied_before_window() throws IOException {
    // Leading 3 spaces + "hello" — after lstrip, beg starts with 'h'.
    byte[] input = new byte[] {0x20, 0x20, 0x20, 'h', 'e', 'l', 'l', 'o'};
    int[] tokens = ByteWindowExtractor.extract(new BytesInput(input), CFG);

    assertThat(tokens[0]).isEqualTo('h');
    assertThat(tokens[1]).isEqualTo('e');
    assertThat(tokens[2]).isEqualTo('l');
    assertThat(tokens[3]).isEqualTo('l');
    assertThat(tokens[4]).isEqualTo('o');
    assertThat(tokens[5]).isEqualTo(256);
  }

  @Test
  void large_file_beg_and_end_disjoint() throws IOException {
    // 16 KB file: beg reads [0, 4096), end reads [N-4096, N) = [12288, 16384).
    int n = 16 * 1024;
    byte[] input = new byte[n];
    for (int i = 0; i < n; i++) {
      // Use a pattern where first bytes != last bytes. 'A' in beg, 'Z' in end.
      input[i] = (byte) (i < 4096 ? 'A' : (i >= n - 4096 ? 'Z' : 'M'));
    }
    int[] tokens = ByteWindowExtractor.extract(new BytesInput(input), CFG);

    // begTokens = first 1024 bytes from beg window (after strip, which is no-op here) — all 'A'.
    assertThat(tokens[0]).isEqualTo('A');
    assertThat(tokens[1023]).isEqualTo('A');
    // endTokens = last 1024 bytes of end window — all 'Z'.
    assertThat(tokens[1024]).isEqualTo('Z');
    assertThat(tokens[2047]).isEqualTo('Z');
  }

  /**
   * D-09 stream-all-slices regression. Feed a ZIP-shape byte[] through a stream whose
   * {@code markSupported()} is false — the extractor must still buffer enough bytes for both
   * {@code beg} and {@code end} windows. The tail "EOCD" sentinel bytes must appear in
   * {@code endTokens}, not be lost to a {@code beg_size}-only read.
   */
  @Test
  void stream_non_seekable_all_slices_buffered_D09() throws IOException {
    // Construct a 10 KB "file": 'A'*4096 + 'M'*(N-4096-22) + "PK\x05\x06..." EOCD-like sentinel.
    int n = 10 * 1024;
    byte[] input = new byte[n];
    for (int i = 0; i < n; i++) {
      input[i] = (byte) 'M';
    }
    for (int i = 0; i < 4096; i++) {
      input[i] = (byte) 'A';
    }
    // EOCD-like 22-byte trailer starting at n-22: PK\x05\x06 then 18 more bytes.
    byte[] eocd = new byte[] {
        'P', 'K', 0x05, 0x06,
        0, 0, 0, 0,
        1, 0, 1, 0,
        0x10, 0, 0, 0,
        0x10, 0, 0, 0,
        0, 0};
    System.arraycopy(eocd, 0, input, n - eocd.length, eocd.length);

    // Wrap in a stream that reports markSupported=false — forces the extractor to buffer.
    InputStream nonMarkable = new FilterInputStream(new ByteArrayInputStream(input)) {
      @Override
      public boolean markSupported() {
        return false;
      }
    };

    int[] tokens = ByteWindowExtractor.extract(new StreamInput(nonMarkable), CFG);

    // begTokens start with 'A' (first 1024 bytes).
    assertThat(tokens[0]).isEqualTo('A');
    // endTokens are the LAST 1024 bytes of the end-window (rstripped, then right-aligned).
    // The last 22 bytes of input are the EOCD record. The last byte is 0x00 — that's a valid
    // non-strip byte (0x00 is NOT in the python strip set per algorithm-notes), so it appears
    // unchanged as the last token.
    assertThat(tokens[2047]).isEqualTo(0x00);
    // Scan back through the end window for the 'PK' signature that lives 22 bytes from EOF.
    // If the stream was truncated to beg_size, tokens[1024..2048) would be all 'M' and the PK
    // signature would be absent.
    boolean foundPk = false;
    for (int i = 1024; i < 2048 - 1; i++) {
      if (tokens[i] == 'P' && tokens[i + 1] == 'K') {
        foundPk = true;
        break;
      }
    }
    assertThat(foundPk)
      .as("EOCD 'PK' signature MUST appear in endTokens — D-09 parity contract")
      .isTrue();
  }

  @Test
  void zero_string_conversion_IO04() throws IOException {
    // Method signature returns int[], not String. Sanity check — a byte 0xFF must arrive in
    // tokens as 255, not as a sign-extended negative int and not as a String codepoint.
    byte[] input = new byte[] {(byte) 0xFF, 'A', 'B', 'C'};
    int[] tokens = ByteWindowExtractor.extract(new BytesInput(input), CFG);

    assertThat(tokens[0]).isEqualTo(0xFF);
    assertThat(tokens[1]).isEqualTo('A');
    assertThat(tokens[0]).isGreaterThan(0); // NOT sign-extended negative
  }

  @Test
  void small_file_beg_and_end_overlap_when_N_less_than_block_size() throws IOException {
    // Per algorithm-notes §Small-file branches (worked N=3000): when N < block_size, the end
    // window reads the same bytes as beg. This is load-bearing parity behavior.
    byte[] input = new byte[500];
    for (int i = 0; i < input.length; i++) {
      input[i] = (byte) ('a' + (i % 26));
    }
    int[] tokens = ByteWindowExtractor.extract(new BytesInput(input), CFG);

    // beg reads [0, 500), no strip, takes first 500, pads to 1024.
    assertThat(tokens[0]).isEqualTo('a');
    assertThat(tokens[499]).isEqualTo((int) ('a' + (499 % 26)));
    assertThat(tokens[500]).isEqualTo(256); // padding

    // end reads [0, 500) (same bytes), no strip, takes last 500, left-pads to 1024.
    // endTokens[0..524) = padding; endTokens[524..1024) = last 500 bytes of input.
    assertThat(tokens[1024]).isEqualTo(256); // left-pad
    assertThat(tokens[1024 + (1024 - 500) - 1]).isEqualTo(256); // last pad
    assertThat(tokens[1024 + (1024 - 500)]).isEqualTo('a'); // first real byte
    assertThat(tokens[2047]).isEqualTo((int) ('a' + (499 % 26))); // last real byte
  }
}
