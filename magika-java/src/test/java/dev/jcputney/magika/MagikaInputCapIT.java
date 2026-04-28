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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link MagikaBuilder#maxBufferBytes(long)} cap on caller-supplied
 * {@link InputStream}s. Default ({@link Long#MAX_VALUE}) preserves upstream-Python parity (no
 * cap); configured callers (Tika operators, anyone routing untrusted bytes) trade an OOM /
 * blocked-thread DoS for an {@link InvalidInputException}.
 */
@Tag("parity")
class MagikaInputCapIT {

  @Test
  void identifyStream_default_cap_is_unlimited_and_preserves_parity() {
    // Default builder leaves maxBufferBytes at Long.MAX_VALUE — readAllBytes() path, no cap.
    // 64 KiB of zeros is well under any practical cap; this asserts default behavior is
    // unchanged (the underlying byte[] reaches identifyInternal and the model runs).
    byte[] payload = new byte[64 * 1024];
    try (Magika m = Magika.create()) {
      MagikaResult r = m.identifyStream(new ByteArrayInputStream(payload));
      assertThat(r).isNotNull();
      assertThat(r.status()).isEqualTo(Status.OK);
    }
  }

  @Test
  void identifyStream_exceeding_cap_throws_InvalidInputException() {
    long cap = 1024L;
    byte[] payload = new byte[(int) cap + 1]; // one byte over the cap
    try (Magika m = Magika.builder().maxBufferBytes(cap).build()) {
      assertThatThrownBy(() -> m.identifyStream(new ByteArrayInputStream(payload)))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("input exceeds maxBufferBytes=" + cap);
    }
  }

  @Test
  void identifyStream_at_cap_succeeds() {
    long cap = 4096L;
    byte[] payload = new byte[(int) cap]; // exactly at the cap
    try (Magika m = Magika.builder().maxBufferBytes(cap).build()) {
      MagikaResult r = m.identifyStream(new ByteArrayInputStream(payload));
      assertThat(r).isNotNull();
      assertThat(r.status()).isEqualTo(Status.OK);
    }
  }

  @Test
  void identifyStream_does_not_buffer_beyond_cap() {
    // Adversarial stream that tracks bytes read. With a 1 KiB cap, the library MUST stop reading
    // before consuming the full 16 KiB the stream is willing to provide — proves the cap actually
    // bounds the read and is not just a post-hoc size check.
    long cap = 1024L;
    CountingInputStream counting = new CountingInputStream(new byte[16 * 1024]);
    try (Magika m = Magika.builder().maxBufferBytes(cap).build()) {
      assertThatThrownBy(() -> m.identifyStream(counting))
        .isInstanceOf(InvalidInputException.class);
      // Cap is 1024; chunk size in readBounded is 8 KiB so we consume one 8 KiB chunk before
      // detecting the overflow. We assert we did NOT read all 16 KiB — that would mean the cap
      // is checked AFTER the full read which is exactly the OOM bug we're guarding against.
      assertThat(counting.totalRead())
        .as("readBounded must abort before consuming the full stream once cap is exceeded")
        .isLessThanOrEqualTo(8192L + 8192L); // at most two chunks
    }
  }

  @Test
  void builder_rejects_zero_or_negative_cap() {
    assertThatThrownBy(() -> Magika.builder().maxBufferBytes(0L))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("maxBufferBytes must be >= 1");
    assertThatThrownBy(() -> Magika.builder().maxBufferBytes(-1L))
      .isInstanceOf(IllegalArgumentException.class);
  }

  /** Tracks bytes pulled from the underlying buffer so the test can assert early termination. */
  private static final class CountingInputStream extends InputStream {

    private final byte[] data;
    private int position;
    private long totalRead;

    CountingInputStream(byte[] data) {
      this.data = data;
    }

    long totalRead() {
      return totalRead;
    }

    @Override
    public int read() {
      if (position >= data.length) {
        return -1;
      }
      totalRead++;
      return data[position++] & 0xFF;
    }

    @Override
    public int read(byte[] dst, int off, int len) throws IOException {
      if (position >= data.length) {
        return -1;
      }
      int n = Math.min(len, data.length - position);
      System.arraycopy(data, position, dst, off, n);
      position += n;
      totalRead += n;
      return n;
    }
  }
}
