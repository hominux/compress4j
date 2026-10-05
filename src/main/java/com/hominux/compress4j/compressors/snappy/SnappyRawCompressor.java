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
package com.hominux.compress4j.compressors.snappy;

import com.hominux.compress4j.compressors.CodecCompressor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream;

/**
 * Provides raw Snappy compression that writes to a {@link SnappyCompressorOutputStream}.
 *
 * @since 3.2
 */
public class SnappyRawCompressor extends CodecCompressor<SnappyCompressorOutputStream> {

    /**
     * Constructor that takes a SnappyCompressorOutputStream.
     *
     * @param compressorOutputStream the SnappyCompressorOutputStream to write to.
     */
    public SnappyRawCompressor(SnappyCompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructor that takes a SnappyRawCompressorBuilder.
     *
     * @param builder the SnappyRawCompressorBuilder to build from.
     * @throws IOException if an I/O error occurred
     */
    public SnappyRawCompressor(SnappyRawCompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link SnappyRawCompressorBuilder} that writes to the given path.
     *
     * @param path the path to write the compressor to
     * @param uncompressedSize the length in bytes of the data that will be written
     * @return a new {@link SnappyRawCompressorBuilder}
     * @throws IOException if an I/O error occurred
     * @throws IllegalArgumentException if {@code uncompressedSize} is negative
     */
    public static SnappyRawCompressorBuilder builder(Path path, long uncompressedSize) throws IOException {
        return new SnappyRawCompressorBuilder(path, uncompressedSize);
    }

    /**
     * Creates a {@link SnappyRawCompressorBuilder} that writes to the given stream.
     *
     * @param outputStream the output stream
     * @param uncompressedSize the length in bytes of the data that will be written
     * @return a new {@link SnappyRawCompressorBuilder}
     * @throws IllegalArgumentException if {@code uncompressedSize} is negative
     */
    public static SnappyRawCompressorBuilder builder(OutputStream outputStream, long uncompressedSize) {
        return new SnappyRawCompressorBuilder(outputStream, uncompressedSize);
    }

    private static long requireNonNegative(long uncompressedSize) {
        if (uncompressedSize < 0) {
            throw new IllegalArgumentException(
                    "Snappy uncompressed size must not be negative, but was: " + uncompressedSize);
        }
        return uncompressedSize;
    }

    /**
     * Builder for creating a {@link SnappyCompressorOutputStream}.
     *
     * @param <P> The type of the parent builder.
     */
    public static class SnappyRawCompressorOutputStreamBuilder<P> {
        private final P parent;
        private final long uncompressedSize;

        /** The output stream to write to. */
        protected final OutputStream outputStream;

        /**
         * Create a new {@link SnappyRawCompressorOutputStreamBuilder} with the given parent and output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write to
         * @param uncompressedSize the length in bytes of the data that will be written
         * @throws IllegalArgumentException if {@code uncompressedSize} is negative
         */
        public SnappyRawCompressorOutputStreamBuilder(P parent, OutputStream outputStream, long uncompressedSize) {
            this.parent = parent;
            this.outputStream = outputStream;
            this.uncompressedSize = requireNonNegative(uncompressedSize);
        }

        /**
         * Builds the {@link SnappyCompressorOutputStream}.
         *
         * @return the {@link SnappyCompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         */
        public SnappyCompressorOutputStream build() throws IOException {
            return new SnappyCompressorOutputStream(outputStream, uncompressedSize);
        }

        /**
         * Returns the parent builder.
         *
         * @return the parent builder
         */
        public P parentBuilder() {
            return parent;
        }
    }

    /** Builder for creating a {@link SnappyRawCompressor}. */
    public static class SnappyRawCompressorBuilder
            extends CompressorBuilder<SnappyCompressorOutputStream, SnappyRawCompressorBuilder, SnappyRawCompressor> {

        private final SnappyRawCompressorOutputStreamBuilder<SnappyRawCompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link SnappyRawCompressorBuilder} with the given path.
         *
         * @param path the path to write the compressor to
         * @param uncompressedSize the length in bytes of the data that will be written
         * @throws IOException if an I/O error occurred
         * @throws IllegalArgumentException if {@code uncompressedSize} is negative
         */
        public SnappyRawCompressorBuilder(Path path, long uncompressedSize) throws IOException {
            this(open(path, uncompressedSize), uncompressedSize, true);
        }

        /**
         * Create a new {@link SnappyRawCompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         * @param uncompressedSize the length in bytes of the data that will be written
         * @throws IllegalArgumentException if {@code uncompressedSize} is negative
         */
        public SnappyRawCompressorBuilder(OutputStream outputStream, long uncompressedSize) {
            this(outputStream, uncompressedSize, false);
        }

        private static OutputStream open(Path path, long uncompressedSize) throws IOException {
            requireNonNegative(uncompressedSize);
            return Files.newOutputStream(path);
        }

        @SuppressWarnings("this-escape")
        private SnappyRawCompressorBuilder(OutputStream outputStream, long uncompressedSize, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder =
                    new SnappyRawCompressorOutputStreamBuilder<>(this, outputStream, uncompressedSize);
        }

        /**
         * Returns the output stream builder for this compressor.
         *
         * @return the {@link SnappyRawCompressorOutputStreamBuilder}
         */
        public SnappyRawCompressorOutputStreamBuilder<SnappyRawCompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public SnappyRawCompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public SnappyCompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /** {@inheritDoc} */
        @Override
        public SnappyRawCompressor build() throws IOException {
            return new SnappyRawCompressor(this);
        }
    }
}
