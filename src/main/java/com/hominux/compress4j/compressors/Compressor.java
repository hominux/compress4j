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

import com.hominux.compress4j.internal.codec.Codecs;
import com.hominux.compress4j.internal.io.Sink;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Compresses data with one {@link Compression} into a path, channel or stream. Closing it finishes the compressed
 * stream and closes the target. Not thread-safe.
 *
 * <p>Compressing to a path creates the file or truncates an existing one. A codec failure after the target opens leaves
 * an empty or partial file.
 *
 * @since 5.0
 */
public final class Compressor implements Closeable {

    private final OutputStream output;

    private Compressor(OutputStream output) {
        this.output = output;
    }

    /**
     * Creates a builder writing to a file; {@link Builder#build()} creates it or truncates an existing file.
     *
     * @param target the file to write
     * @param compression the codec
     * @return the builder
     * @throws IllegalArgumentException if the codec can only be read or lacks a required option
     */
    public static Builder builder(Path target, Compression compression) {
        return new Builder(new Sink.OfPath(target), compression);
    }

    /**
     * Creates a builder writing to a channel from its current position.
     *
     * @param target the channel; {@link Compressor#close()} closes it
     * @param compression the codec
     * @return the builder
     * @throws IllegalArgumentException if the codec can only be read or lacks a required option
     */
    public static Builder builder(SeekableByteChannel target, Compression compression) {
        return new Builder(new Sink.OfChannel(target), compression);
    }

    /**
     * Creates a builder writing to a stream.
     *
     * @param target the stream; {@link Compressor#close()} closes it
     * @param compression the codec
     * @return the builder
     * @throws IllegalArgumentException if the codec can only be read or lacks a required option
     */
    public static Builder builder(OutputStream target, Compression compression) {
        return new Builder(new Sink.OfStream(target), compression);
    }

    /**
     * Compresses a file's content.
     *
     * @param source the file to read
     * @return the number of bytes read
     * @throws IOException if reading or writing fails
     */
    public long write(Path source) throws IOException {
        return Files.copy(source, output);
    }

    /**
     * Compresses a stream's remaining content; the stream is not closed.
     *
     * @param source the stream to read
     * @return the number of bytes read
     * @throws IOException if reading or writing fails
     */
    public long write(InputStream source) throws IOException {
        return source.transferTo(output);
    }

    /**
     * Returns the compressing stream; closing it closes this compressor.
     *
     * @return the stream
     */
    public OutputStream outputStream() {
        return output;
    }

    @Override
    public void close() throws IOException {
        output.close();
    }

    /** Builds a {@link Compressor}; opens the target only in {@link #build()}. */
    public static final class Builder {
        private final Sink sink;
        private final Compression compression;

        private Builder(Sink sink, Compression compression) {
            this.sink = sink;
            this.compression = Objects.requireNonNull(compression, "compression");
            Codecs.requireWritable(compression);
        }

        /**
         * Opens the target and starts the compressed stream. If this fails, a target this builder opened is closed; a
         * caller's channel or stream is left open.
         *
         * @return the compressor
         * @throws IOException if the target cannot be opened or the codec cannot start
         */
        public Compressor build() throws IOException {
            Sink.Opened opened = sink.open();
            try {
                return new Compressor(Codecs.compressing(compression, opened.out()));
            } catch (IOException e) {
                throw opened.closeIfOwned(e);
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }
    }
}
