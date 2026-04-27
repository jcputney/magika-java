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

import dev.jcputney.magika.config.ThresholdConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Turns an {@link InputSource} into the concatenated {@code beg + mid + end} int[] tensor
 * expected by {@code InferenceEngine.run(...)} (IO-02, IO-04). Algorithm mirrors upstream Python
 * feature extraction in {@code magika.py:_extract_features_from_seekable} verbatim — see
 * {@code docs/algorithm-notes.md} §"Small-file branches" (worked N=3000 walkthrough) and
 * §"Stream handling" (D-09 all-slices buffering contract).
 *
 * <p><strong>Strip-then-window:</strong> {@link ByteStrip} is applied to the raw block BEFORE
 * slicing to {@code beg_size} / {@code end_size}. Reversing the order silently breaks parity on
 * any file whose first/last bytes happen to be in the strip set.
 *
 * <p><strong>Zero String conversion (IO-04):</strong> operates end-to-end on {@code byte[]}.
 * Signed bytes are widened to unsigned ints via {@code (b & 0xFF)} for the model's
 * {@code [0, 256]} token vocabulary.
 *
 * <p><strong>Stream buffer-all-slices (D-09):</strong> for {@link StreamInput}, the full stream
 * (or at minimum enough bytes to cover the first block and the trailing block) must be buffered
 * — an implementation that reads only {@code beg_size} bytes silently breaks parity on
 * tail-signal formats (ZIP end-of-central-directory, TAR trailer, PDF {@code %%EOF}).
 */
public final class ByteWindowExtractor {

  private ByteWindowExtractor() {
    // utility class
  }

  /**
   * Extract the 2048-int tensor row (for {@code standard_v3_3}) from {@code src}, using
   * {@code cfg}'s window sizes and padding token. Throws {@link IOException} on underlying
   * filesystem / stream read failure; callers in the facade layer (Plan 5) translate to
   * {@code InvalidInputException} / {@code InferenceException}.
   */
  public static int[] extract(InputSource src, ThresholdConfig cfg) throws IOException {
    int totalLen = cfg.begSize() + cfg.midSize() + cfg.endSize();
    int[] tokens = new int[totalLen];

    if (src instanceof BytesInput b) {
      return buildFromBytes(b.bytes(), cfg, tokens);
    }
    if (src instanceof PathInput p) {
      return buildFromPath(p.path(), cfg, tokens);
    }
    if (src instanceof StreamInput s) {
      return buildFromStream(s.stream(), cfg, tokens);
    }
    throw new IllegalStateException("unreachable: unknown InputSource kind");
  }

  private static int[] buildFromBytes(byte[] bytes, ThresholdConfig cfg, int[] tokens) {
    int n = bytes.length;
    int bytesNumToRead = Math.min(cfg.blockSize(), n);
    int endReadOffset = Math.max(0, n - bytesNumToRead);
    fillBegTokens(bytes, 0, bytesNumToRead, cfg, tokens);
    fillMidTokens(cfg, tokens);
    fillEndTokens(bytes, endReadOffset, bytesNumToRead, cfg, tokens);
    return tokens;
  }

  private static int[] buildFromPath(Path path, ThresholdConfig cfg, int[] tokens)
    throws IOException {
    long size = Files.size(path);
    int blockSize = cfg.blockSize();
    int bytesNumToRead = (int) Math.min((long) blockSize, size);
    byte[] begRaw = new byte[bytesNumToRead];
    byte[] endRaw = new byte[bytesNumToRead];
    try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
      readFully(ch, ByteBuffer.wrap(begRaw), 0);
      long endReadOffset = Math.max(0L, size - bytesNumToRead);
      readFully(ch, ByteBuffer.wrap(endRaw), endReadOffset);
    }
    fillBegTokens(begRaw, 0, bytesNumToRead, cfg, tokens);
    fillMidTokens(cfg, tokens);
    fillEndTokens(endRaw, 0, bytesNumToRead, cfg, tokens);
    return tokens;
  }

  private static int[] buildFromStream(InputStream stream, ThresholdConfig cfg, int[] tokens)
    throws IOException {
    // D-09: we must buffer enough bytes to cover beg + end, which means up to the full stream for
    // non-seekable streams. Simplest-correct strategy: read to EOF into a byte[] (upstream Python
    // has no max-size either per D-10). For streams > 2*block_size this materializes the whole
    // stream; that's upstream-parity behavior.
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = stream.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
    }
    return buildFromBytes(buffer.toByteArray(), cfg, tokens);
  }

  private static void readFully(FileChannel ch, ByteBuffer buf, long position) throws IOException {
    while (buf.hasRemaining()) {
      int read = ch.read(buf, position + buf.position());
      if (read < 0) {
        break;
      }
    }
  }

  private static void fillBegTokens(
    byte[] raw, int offset, int length, ThresholdConfig cfg, int[] tokens) {
    int begSize = cfg.begSize();
    int padding = cfg.paddingToken();
    // Step: lstrip the raw block BEFORE windowing (strip-then-window per IO-02).
    int stripCount = ByteStrip.leadingStripCount(raw, offset, length);
    int start = offset + stripCount;
    int available = length - stripCount;
    int copyLen = Math.min(begSize, available);
    for (int i = 0; i < copyLen; i++) {
      tokens[i] = raw[start + i] & 0xFF;
    }
    for (int i = copyLen; i < begSize; i++) {
      tokens[i] = padding;
    }
  }

  private static void fillMidTokens(ThresholdConfig cfg, int[] tokens) {
    int begSize = cfg.begSize();
    int midSize = cfg.midSize();
    int padding = cfg.paddingToken();
    for (int i = 0; i < midSize; i++) {
      tokens[begSize + i] = padding;
    }
  }

  private static void fillEndTokens(
    byte[] raw, int offset, int length, ThresholdConfig cfg, int[] tokens) {
    int begSize = cfg.begSize();
    int midSize = cfg.midSize();
    int endSize = cfg.endSize();
    int padding = cfg.paddingToken();
    // Step: rstrip the raw block BEFORE windowing.
    int stripCount = ByteStrip.trailingStripCount(raw, offset, length);
    int available = length - stripCount;
    int copyLen = Math.min(endSize, available);
    // Left-pad end window with padding token, right-fill with stripped-end bytes.
    int padCount = endSize - copyLen;
    int endBase = begSize + midSize;
    for (int i = 0; i < padCount; i++) {
      tokens[endBase + i] = padding;
    }
    // Source bytes: last `copyLen` bytes of the stripped range (raw[offset..offset+available)).
    int srcStart = offset + available - copyLen;
    for (int i = 0; i < copyLen; i++) {
      tokens[endBase + padCount + i] = raw[srcStart + i] & 0xFF;
    }
  }
}
