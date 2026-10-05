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
package com.hominux.compress4j.compressors.zstd;

import com.github.luben.zstd.Zstd;
import com.hominux.compress4j.compressors.CodecCompressor;
import com.hominux.compress4j.utils.ArchiverDependencyChecker;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;

/**
 * Provides Zstandard compression that writes to a {@link ZstdCompressorOutputStream}. Requires
 * {@code com.github.luben:zstd-jni} at runtime.
 *
 * @since 3.2
 */
public class ZstdCompressor extends CodecCompressor<ZstdCompressorOutputStream> {

    /**
     * Constructor that takes a ZstdCompressorOutputStream.
     *
     * @param compressorOutputStream the ZstdCompressorOutputStream to write to.
     */
    public ZstdCompressor(ZstdCompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructor that takes a ZstdCompressorBuilder.
     *
     * @param builder the ZstdCompressorBuilder to build from.
     * @throws IOException if an I/O error occurred
     */
    public ZstdCompressor(ZstdCompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link ZstdCompressorBuilder} that writes to the given path.
     *
     * @param path the path to write the compressor to
     * @return a new {@link ZstdCompressorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static ZstdCompressorBuilder builder(Path path) throws IOException {
        return new ZstdCompressorBuilder(path);
    }

    /**
     * Creates a {@link ZstdCompressorBuilder} that writes to the given stream.
     *
     * @param outputStream the output stream
     * @return a new {@link ZstdCompressorBuilder}
     */
    public static ZstdCompressorBuilder builder(OutputStream outputStream) {
        return new ZstdCompressorBuilder(outputStream);
    }

    /**
     * Builder for creating a {@link ZstdCompressorOutputStream}.
     *
     * @param <P> The type of the parent builder.
     */
    public static class ZstdCompressorOutputStreamBuilder<P> {
        private static final int DEFAULT_LEVEL = 3;

        private final P parent;
        private int level = DEFAULT_LEVEL;

        /** The output stream to write to. */
        protected final OutputStream outputStream;

        /**
         * Create a new {@link ZstdCompressorOutputStreamBuilder} with the given parent and output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write to
         */
        public ZstdCompressorOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Sets the compression level. The default is 3.
         *
         * @param level the level, between {@link Zstd#minCompressionLevel()} and {@link Zstd#maxCompressionLevel()}
         * @return this builder instance
         * @throws IllegalArgumentException if the level is outside the supported range
         * @throws com.hominux.compress4j.exceptions.MissingArchiveDependencyException if zstd-jni is not available
         */
        public ZstdCompressorOutputStreamBuilder<P> level(int level) {
            ArchiverDependencyChecker.checkZstd();
            int min = Zstd.minCompressionLevel();
            int max = Zstd.maxCompressionLevel();
            if (level < min || level > max) {
                throw new IllegalArgumentException(
                        "Zstd level must be in the range [" + min + ", " + max + "], but was: " + level);
            }
            this.level = level;
            return this;
        }

        /**
         * Builds the {@link ZstdCompressorOutputStream} with the configured level.
         *
         * @return the {@link ZstdCompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         * @throws com.hominux.compress4j.exceptions.MissingArchiveDependencyException if zstd-jni is not available
         */
        public ZstdCompressorOutputStream build() throws IOException {
            ArchiverDependencyChecker.checkZstd();
            return ZstdCompressorOutputStream.builder()
                    .setOutputStream(outputStream)
                    .setLevel(level)
                    .get();
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

    /** Builder for creating a {@link ZstdCompressor}. */
    public static class ZstdCompressorBuilder
            extends CompressorBuilder<ZstdCompressorOutputStream, ZstdCompressorBuilder, ZstdCompressor> {

        private final ZstdCompressorOutputStreamBuilder<ZstdCompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link ZstdCompressorBuilder} with the given path.
         *
         * @param path the path to write the compressor to
         * @throws IOException if an I/O error occurred
         */
        public ZstdCompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link ZstdCompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        public ZstdCompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private ZstdCompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new ZstdCompressorOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Returns the output stream builder for this compressor.
         *
         * @return the {@link ZstdCompressorOutputStreamBuilder}
         */
        public ZstdCompressorOutputStreamBuilder<ZstdCompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public ZstdCompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public ZstdCompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /** {@inheritDoc} */
        @Override
        public ZstdCompressor build() throws IOException {
            return new ZstdCompressor(this);
        }
    }
}
