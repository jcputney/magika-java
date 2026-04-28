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

package dev.jcputney.magika.tika;

import dev.jcputney.magika.DetectedContentType;
import dev.jcputney.magika.Magika;
import dev.jcputney.magika.MagikaException;
import dev.jcputney.magika.PredictionMode;
import dev.jcputney.magika.Status;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;

/**
 * Apache Tika {@link Detector} backed by embedded {@code magika-java}.
 *
 * <p>Unlike Tika's external-process Magika detector, this adapter does not require the Python
 * {@code magika} command to be installed. It runs the bundled ONNX model through the core
 * {@link Magika} library and emits metadata using the same {@code magika:*} key family.
 */
public final class MagikaTikaDetector implements Detector, AutoCloseable {

  private static final long serialVersionUID = 1L;

  public static final String MAGIKA_PREFIX = "magika:";
  public static final Property MAGIKA_STATUS = Property.externalText(MAGIKA_PREFIX + "status");
  public static final Property MAGIKA_DESCRIPTION =
    Property.externalText(MAGIKA_PREFIX + "description");
  public static final Property MAGIKA_SCORE = Property.externalReal(MAGIKA_PREFIX + "score");
  public static final Property MAGIKA_GROUP = Property.externalText(MAGIKA_PREFIX + "group");
  public static final Property MAGIKA_LABEL = Property.externalText(MAGIKA_PREFIX + "label");
  public static final Property MAGIKA_MIME = Property.externalText(MAGIKA_PREFIX + "mime_type");
  public static final Property MAGIKA_IS_TEXT = Property.externalBoolean(MAGIKA_PREFIX + "is_text");
  public static final Property MAGIKA_ERRORS = Property.externalTextBag(MAGIKA_PREFIX + "errors");
  public static final Property MAGIKA_VERSION = Property.externalText(MAGIKA_PREFIX + "version");

  private final PredictionMode predictionMode;
  private final UnknownHandling unknownHandling;
  private final boolean emitMetadata;
  private final long maxInputBytes;
  // volatile + double-checked locking. Without volatile the fast-path read in ensureMagika()
  // can observe a partially-constructed Magika under the JMM. Mirrors the pattern used by core
  // dev.jcputney.magika.Magika for its lazy InferenceEngine. transient keeps the field out of
  // serialization; the instance is reinitialized on first detect() after deserialize.
  private transient volatile Magika magika;

  /** Creates a detector with default core prediction mode and null-on-unknown behavior. */
  public MagikaTikaDetector() {
    this(builder());
  }

  private MagikaTikaDetector(Builder builder) {
    this.predictionMode = builder.predictionMode;
    this.unknownHandling = builder.unknownHandling;
    this.emitMetadata = builder.emitMetadata;
    this.maxInputBytes = builder.maxInputBytes;
  }

  /** Returns a new detector builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** Configures what {@link #detect(InputStream, Metadata)} returns for non-useful detections. */
  public enum UnknownHandling {
    /** Return null so Tika composite detector chains can continue. */
    RETURN_NULL,

    /** Return {@link MediaType#OCTET_STREAM}. */
    OCTET_STREAM
  }


  /** Builder for {@link MagikaTikaDetector}. */
  public static final class Builder {

    private PredictionMode predictionMode = PredictionMode.DEFAULT;
    private UnknownHandling unknownHandling = UnknownHandling.RETURN_NULL;
    private boolean emitMetadata = true;
    private long maxInputBytes = Long.MAX_VALUE;

    private Builder() {
      // callers use MagikaTikaDetector.builder()
    }

    /** Sets the core Magika prediction mode. */
    public Builder predictionMode(PredictionMode predictionMode) {
      this.predictionMode = Objects.requireNonNull(predictionMode, "predictionMode");
      return this;
    }

    /** Sets the return behavior for unknown, empty, octet-stream, or status-error detections. */
    public Builder unknownHandling(UnknownHandling unknownHandling) {
      this.unknownHandling = Objects.requireNonNull(unknownHandling, "unknownHandling");
      return this;
    }

    /** Enables or disables writing {@code magika:*} metadata keys. */
    public Builder emitMetadata(boolean emitMetadata) {
      this.emitMetadata = emitMetadata;
      return this;
    }

    /**
     * Caps the bytes read from the {@link InputStream} passed to
     * {@link MagikaTikaDetector#detect(InputStream, Metadata)}. Defaults to {@link Long#MAX_VALUE}
     * (unlimited) which preserves prior behavior — Tika's own composite-detector chain may pass
     * very large streams. Operators behind upload pipelines accepting untrusted bytes set a
     * finite cap (e.g. {@code 64L * 1024 * 1024}) to avoid OOM / blocked-thread DoS.
     *
     * @param bytes positive cap, or {@link Long#MAX_VALUE} to disable
     * @throws IllegalArgumentException if {@code bytes < 1}
     */
    public Builder maxInputBytes(long bytes) {
      if (bytes < 1L) {
        throw new IllegalArgumentException("maxInputBytes must be >= 1, got " + bytes);
      }
      this.maxInputBytes = bytes;
      return this;
    }

    /** Builds a detector. The underlying ONNX session is still initialized lazily. */
    public MagikaTikaDetector build() {
      return new MagikaTikaDetector(this);
    }
  }

  @Override
  public MediaType detect(InputStream input, Metadata metadata) throws IOException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(metadata, "metadata");

    Magika local = ensureMagika();
    DetectedContentType detected;
    try {
      detected = local.detectBytes(readPreservingMark(input, maxInputBytes));
    } catch (MagikaException | IllegalStateException e) {
      if (emitMetadata) {
        metadata.set(MAGIKA_STATUS, "error");
        metadata.add(MAGIKA_ERRORS, e.getMessage());
      }
      throw new IOException("Magika detection failed", e);
    }

    if (emitMetadata) {
      writeMetadata(metadata, detected, local);
    }

    MediaType mediaType = usefulMediaType(detected);
    if (mediaType != null) {
      return mediaType;
    }
    return unknownHandling == UnknownHandling.OCTET_STREAM ? MediaType.OCTET_STREAM : null;
  }

  @Override
  public void close() {
    Magika local = magika;
    if (local != null) {
      local.close();
      magika = null;
    }
  }

  private Magika ensureMagika() {
    Magika local = magika;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      if (magika == null) {
        magika = Magika.builder().predictionMode(predictionMode).build();
      }
      return magika;
    }
  }

  /**
   * Read the input into a byte[], capped at {@code maxBytes}. When uncapped (Long.MAX_VALUE)
   * preserves the prior unbounded behavior. When capped, throws {@link IOException} once the
   * cumulative read exceeds {@code maxBytes} so attackers cannot trickle bytes one chunk at a
   * time. The {@link InputStream#mark(int)} limit is bounded by the cap (or 8 MiB for the uncapped
   * default — Integer.MAX_VALUE forced buffered streams to retain absurd state).
   */
  private static byte[] readPreservingMark(InputStream input, long maxBytes) throws IOException {
    int markLimit = (int) Math.min(maxBytes, Integer.MAX_VALUE);
    if (input.markSupported()) {
      input.mark(markLimit);
    }
    try {
      return readBounded(input, maxBytes);
    } finally {
      if (input.markSupported()) {
        try {
          input.reset();
        } catch (IOException ignored) {
          // Reset may legitimately fail if the underlying buffer overflowed beyond the mark
          // limit; Tika's composite-detector chain only requires the contract on streams that
          // honor mark/reset, and our cap means we never read past the mark limit.
        }
      }
    }
  }

  private static byte[] readBounded(InputStream input, long maxBytes) throws IOException {
    if (maxBytes >= Long.MAX_VALUE) {
      return input.readAllBytes();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    long total = 0L;
    int read;
    while ((read = input.read(chunk)) != -1) {
      total += read;
      if (total > maxBytes) {
        throw new IOException("input exceeds maxInputBytes=" + maxBytes);
      }
      out.write(chunk, 0, read);
    }
    return out.toByteArray();
  }

  private static void writeMetadata(
    Metadata metadata,
    DetectedContentType detected,
    Magika magika) {
    metadata.set(MAGIKA_STATUS, statusValue(detected.status()));
    setIfPresent(metadata, MAGIKA_DESCRIPTION, detected.description());
    metadata.set(MAGIKA_SCORE, detected.score());
    setIfPresent(metadata, MAGIKA_GROUP, detected.group());
    metadata.set(MAGIKA_LABEL, detected.label());
    setIfPresent(metadata, MAGIKA_MIME, detected.mimeType());
    metadata.set(MAGIKA_IS_TEXT, detected.isText());
    metadata.set(MAGIKA_VERSION, magika.getModelVersion());
  }

  private static void setIfPresent(Metadata metadata, Property property, String value) {
    if (value != null && !value.isBlank()) {
      metadata.set(property, value);
    }
  }

  private static String statusValue(Status status) {
    return status.name().toLowerCase(Locale.ROOT);
  }

  private static MediaType usefulMediaType(DetectedContentType detected) {
    if (detected.status() != Status.OK || detected.isUnknownLike()) {
      return null;
    }
    if (detected.mimeType() == null || detected.mimeType().isBlank()) {
      return null;
    }
    MediaType parsed = MediaType.parse(detected.mimeType());
    if (parsed == null || MediaType.OCTET_STREAM.equals(parsed)) {
      return null;
    }
    return parsed;
  }
}
