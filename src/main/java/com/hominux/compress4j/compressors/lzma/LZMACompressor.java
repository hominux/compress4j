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
package com.hominux.compress4j.compressors.lzma;

import com.hominux.compress4j.compressors.CodecCompressor;
import com.hominux.compress4j.utils.ArchiverDependencyChecker;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;

/**
 * Provides LZMA compression that writes to a {@link LZMACompressorOutputStream}. Requires {@code org.tukaani:xz} at
 * runtime.
 *
 * @since 3.2
 */
public class LZMACompressor extends CodecCompressor<LZMACompressorOutputStream> {

    /**
     * Constructor that takes a LZMACompressorOutputStream.
     *
     * @param compressorOutputStream the LZMACompressorOutputStream to write to.
     */
    public LZMACompressor(LZMACompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructor that takes a LZMACompressorBuilder.
     *
     * @param builder the LZMACompressorBuilder to build from.
     * @throws IOException if an I/O error occurred
     */
    public LZMACompressor(LZMACompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link LZMACompressorBuilder} that writes to the given path.
     *
     * @param path the path to write the compressor to
     * @return a new {@link LZMACompressorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static LZMACompressorBuilder builder(Path path) throws IOException {
        return new LZMACompressorBuilder(path);
    }

    /**
     * Creates a {@link LZMACompressorBuilder} that writes to the given stream.
     *
     * @param outputStream the output stream
     * @return a new {@link LZMACompressorBuilder}
     */
    public static LZMACompressorBuilder builder(OutputStream outputStream) {
        return new LZMACompressorBuilder(outputStream);
    }

    /**
     * Builder for creating a {@link LZMACompressorOutputStream}.
     *
     * @param <P> The type of the parent builder.
     */
    public static class LZMACompressorOutputStreamBuilder<P> {
        private final P parent;

        /** The output stream to write to. */
        protected final OutputStream outputStream;

        /**
         * Create a new {@link LZMACompressorOutputStreamBuilder} with the given parent and output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write to
         */
        public LZMACompressorOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Builds the {@link LZMACompressorOutputStream}.
         *
         * @return the {@link LZMACompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         * @throws com.hominux.compress4j.exceptions.MissingArchiveDependencyException if org.tukaani:xz is not
         *     available
         */
        public LZMACompressorOutputStream build() throws IOException {
            ArchiverDependencyChecker.checkLZMA();
            return LZMACompressorOutputStream.builder()
                    .setOutputStream(outputStream)
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

    /** Builder for creating a {@link LZMACompressor}. */
    public static class LZMACompressorBuilder
            extends CompressorBuilder<LZMACompressorOutputStream, LZMACompressorBuilder, LZMACompressor> {

        private final LZMACompressorOutputStreamBuilder<LZMACompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link LZMACompressorBuilder} with the given path.
         *
         * @param path the path to write the compressor to
         * @throws IOException if an I/O error occurred
         */
        public LZMACompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link LZMACompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        public LZMACompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private LZMACompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new LZMACompressorOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Returns the output stream builder for this compressor.
         *
         * @return the {@link LZMACompressorOutputStreamBuilder}
         */
        public LZMACompressorOutputStreamBuilder<LZMACompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public LZMACompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public LZMACompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /** {@inheritDoc} */
        @Override
        public LZMACompressor build() throws IOException {
            return new LZMACompressor(this);
        }
    }
}
