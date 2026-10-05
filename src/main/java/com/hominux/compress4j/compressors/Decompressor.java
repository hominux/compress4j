/*
 * Copyright 2026 The Compress4J Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hominux.compress4j.compressors;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.internal.codec.Codecs;
import com.hominux.compress4j.internal.io.ParserFailures;
import com.hominux.compress4j.internal.io.Source;
import com.hominux.compress4j.internal.limits.ExpansionMeter;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/**
 * Decompresses one compressed stream and enforces {@link ExtractionLimits}. Only {@code maxTotalSize} and
 * {@code maxRatio} apply; {@code maxEntries} and {@code maxEntrySize} are ignored. The ratio is enforced only after 1
 * MiB of output ({@code ExpansionMeter.RATIO_GRACE_BYTES}).
 *
 * <p>Corrupt compressed data fails with {@link IOException}, never a parser RuntimeException.
 *
 * <p>Closing it or {@link #inputStream()} also closes a caller-supplied stream or channel. It is not thread-safe.
 * Detection decompresses concatenated members and never selects Pack200. An explicitly chosen Pack200 decodes eagerly
 * in {@link Builder#build()}, before metering.
 *
 * @since 5.0
 */
public final class Decompressor implements Closeable {
    private static final String COMPRESSION = "compression";

    private final InputStream input;
    private final boolean overwrite;

    private Decompressor(InputStream input, boolean overwrite) {
        this.input = input;
        this.overwrite = overwrite;
    }

    /**
     * Creates a builder reading a file and detecting its codec.
     *
     * @param source the file to read
     * @return the builder
     */
    public static Builder builder(Path source) {
        return new Builder(new Source.OfPath(source), Optional.empty());
    }

    /**
     * Creates a builder reading a file with the given codec.
     *
     * @param source the file to read
     * @param compression the codec
     * @return the builder
     */
    public static Builder builder(Path source, Compression compression) {
        return new Builder(new Source.OfPath(source), Optional.of(Objects.requireNonNull(compression, COMPRESSION)));
    }

    /**
     * Creates a builder reading a channel from its position and detecting its codec.
     *
     * @param source the channel to read
     * @return the builder
     */
    public static Builder builder(SeekableByteChannel source) {
        return new Builder(new Source.OfChannel(source), Optional.empty());
    }

    /**
     * Creates a builder reading a channel from its position with the given codec.
     *
     * @param source the channel to read
     * @param compression the codec
     * @return the builder
     */
    public static Builder builder(SeekableByteChannel source, Compression compression) {
        return new Builder(new Source.OfChannel(source), Optional.of(Objects.requireNonNull(compression, COMPRESSION)));
    }

    /**
     * Creates a builder reading a stream and detecting its codec.
     *
     * @param source the stream to read
     * @return the builder
     */
    public static Builder builder(InputStream source) {
        return new Builder(new Source.OfStream(source), Optional.empty());
    }

    /**
     * Creates a builder reading a stream with the given codec.
     *
     * @param source the stream to read
     * @param compression the codec
     * @return the builder
     */
    public static Builder builder(InputStream source, Compression compression) {
        return new Builder(new Source.OfStream(source), Optional.of(Objects.requireNonNull(compression, COMPRESSION)));
    }

    /**
     * Writes the decompressed data to a file. A failed write deletes the file.
     *
     * @param target the file to write
     * @return the number of bytes written
     * @throws FileAlreadyExistsException if the file exists and overwrite is off; the file is left untouched
     * @throws IOException if reading, writing, a limit or corrupt compressed data fails
     */
    public long write(Path target) throws IOException {
        try {
            return overwrite
                    ? Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                    : Files.copy(input, target);
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (IOException e) {
            throw discard(target, e);
        } catch (RuntimeException e) {
            throw discard(target, e);
        }
    }

    private static <T extends Throwable> T discard(Path target, T failure) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException deleteFailure) {
            failure.addSuppressed(deleteFailure);
        }
        return failure;
    }

    /**
     * Returns the decompressed stream, metered against the limits; closing it closes this decompressor.
     *
     * @return the stream
     */
    public InputStream inputStream() {
        return input;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    /** Builds a {@link Decompressor}; opens the source only in {@link #build()}. */
    public static final class Builder {
        private final Source source;
        private final Optional<Compression> compression;
        private boolean overwrite;
        private ExtractionLimits limits = ExtractionLimits.defaults();

        private Builder(Source source, Optional<Compression> compression) {
            this.source = source;
            this.compression = compression;
        }

        /**
         * Sets whether {@link Decompressor#write(Path)} may replace an existing file; off by default.
         *
         * @param overwrite whether to replace an existing target
         * @return this builder
         */
        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        /**
         * Replaces all limits; the default is {@link ExtractionLimits#defaults()}.
         *
         * @param limits the limits to enforce
         * @return this builder
         */
        public Builder limits(ExtractionLimits limits) {
            this.limits = Objects.requireNonNull(limits, "limits");
            return this;
        }

        /**
         * Sets the maximum decompressed bytes.
         *
         * @param max the maximum, or {@link ExtractionLimits#UNLIMITED}
         * @return this builder
         * @throws IllegalArgumentException if the value is below 0 and not unlimited
         */
        public Builder maxTotalSize(long max) {
            this.limits = limits.withMaxTotalSize(max);
            return this;
        }

        /**
         * Sets the maximum decompressed-to-compressed ratio.
         *
         * @param max the maximum, at least 1, or {@link ExtractionLimits#UNLIMITED}
         * @return this builder
         * @throws IllegalArgumentException if the value is below 1 and not unlimited
         */
        public Builder maxRatio(long max) {
            this.limits = limits.withMaxRatio(max);
            return this;
        }

        /**
         * Opens the source, detects the codec when none was given, and starts decompressing. If this fails, a source
         * this builder opened is closed; a caller's channel or stream is left open.
         *
         * @return the decompressor
         * @throws IOException if the source cannot be opened, the codec cannot start or the header is corrupt
         */
        public Decompressor build() throws IOException {
            Source.Opened opened = source.open();
            try {
                return new Decompressor(decode(opened), overwrite);
            } catch (IOException e) {
                throw opened.closeIfOwned(e);
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }

        private InputStream decode(Source.Opened opened) throws IOException {
            InputStream buffered = new BufferedInputStream(opened.in());
            Compression codec = compression.isPresent()
                    ? compression.orElseThrow()
                    : ParserFailures.call(() -> Codecs.detectForReading(buffered), ParserFailures.COMPRESSED_DATA);
            InputStream decoded =
                    ParserFailures.call(() -> Codecs.decompressing(codec, buffered), ParserFailures.COMPRESSED_DATA);
            InputStream parsed = ParserFailures.wrap(decoded, ParserFailures.COMPRESSED_DATA);
            return new ExpansionMeter(limits, opened.in()::count).meter(parsed, Optional.empty());
        }
    }
}
