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
package com.hominux.compress4j.compressors.lz4;

import com.hominux.compress4j.compressors.CodecCompressor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorOutputStream;

/**
 * Provides block LZ4 compression that writes to a {@link BlockLZ4CompressorOutputStream}.
 *
 * @since 3.2
 */
public class Lz4BlockCompressor extends CodecCompressor<BlockLZ4CompressorOutputStream> {

    /**
     * Constructor that takes a BlockLZ4CompressorOutputStream.
     *
     * @param compressorOutputStream the BlockLZ4CompressorOutputStream to write to.
     */
    public Lz4BlockCompressor(BlockLZ4CompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructor that takes a Lz4BlockCompressorBuilder.
     *
     * @param builder the Lz4BlockCompressorBuilder to build from.
     * @throws IOException if an I/O error occurred
     */
    public Lz4BlockCompressor(Lz4BlockCompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link Lz4BlockCompressorBuilder} that writes to the given path.
     *
     * @param path the path to write the compressor to
     * @return a new {@link Lz4BlockCompressorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static Lz4BlockCompressorBuilder builder(Path path) throws IOException {
        return new Lz4BlockCompressorBuilder(path);
    }

    /**
     * Creates a {@link Lz4BlockCompressorBuilder} that writes to the given stream.
     *
     * @param outputStream the output stream
     * @return a new {@link Lz4BlockCompressorBuilder}
     */
    public static Lz4BlockCompressorBuilder builder(OutputStream outputStream) {
        return new Lz4BlockCompressorBuilder(outputStream);
    }

    /**
     * Builder for creating a {@link BlockLZ4CompressorOutputStream}.
     *
     * @param <P> The type of the parent builder.
     */
    public static class Lz4BlockCompressorOutputStreamBuilder<P> {
        private final P parent;

        /** The output stream to write to. */
        protected final OutputStream outputStream;

        /**
         * Create a new {@link Lz4BlockCompressorOutputStreamBuilder} with the given parent and output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write to
         */
        public Lz4BlockCompressorOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Builds the {@link BlockLZ4CompressorOutputStream}.
         *
         * @return the {@link BlockLZ4CompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         */
        public BlockLZ4CompressorOutputStream build() throws IOException {
            return new BlockLZ4CompressorOutputStream(outputStream);
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

    /** Builder for creating a {@link Lz4BlockCompressor}. */
    public static class Lz4BlockCompressorBuilder
            extends CompressorBuilder<BlockLZ4CompressorOutputStream, Lz4BlockCompressorBuilder, Lz4BlockCompressor> {

        private final Lz4BlockCompressorOutputStreamBuilder<Lz4BlockCompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link Lz4BlockCompressorBuilder} with the given path.
         *
         * @param path the path to write the compressor to
         * @throws IOException if an I/O error occurred
         */
        public Lz4BlockCompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link Lz4BlockCompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        public Lz4BlockCompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private Lz4BlockCompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new Lz4BlockCompressorOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Returns the output stream builder for this compressor.
         *
         * @return the {@link Lz4BlockCompressorOutputStreamBuilder}
         */
        public Lz4BlockCompressorOutputStreamBuilder<Lz4BlockCompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public Lz4BlockCompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public BlockLZ4CompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /** {@inheritDoc} */
        @Override
        public Lz4BlockCompressor build() throws IOException {
            return new Lz4BlockCompressor(this);
        }
    }
}
