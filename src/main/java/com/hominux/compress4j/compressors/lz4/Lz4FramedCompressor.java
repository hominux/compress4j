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
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;

/**
 * Provides framed LZ4 compression that writes to a {@link FramedLZ4CompressorOutputStream}.
 *
 * @since 3.2
 */
public class Lz4FramedCompressor extends CodecCompressor<FramedLZ4CompressorOutputStream> {

    /**
     * Constructor that takes a FramedLZ4CompressorOutputStream.
     *
     * @param compressorOutputStream the FramedLZ4CompressorOutputStream to write to.
     */
    public Lz4FramedCompressor(FramedLZ4CompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructor that takes a Lz4FramedCompressorBuilder.
     *
     * @param builder the Lz4FramedCompressorBuilder to build from.
     * @throws IOException if an I/O error occurred
     */
    public Lz4FramedCompressor(Lz4FramedCompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link Lz4FramedCompressorBuilder} that writes to the given path.
     *
     * @param path the path to write the compressor to
     * @return a new {@link Lz4FramedCompressorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static Lz4FramedCompressorBuilder builder(Path path) throws IOException {
        return new Lz4FramedCompressorBuilder(path);
    }

    /**
     * Creates a {@link Lz4FramedCompressorBuilder} that writes to the given stream.
     *
     * @param outputStream the output stream
     * @return a new {@link Lz4FramedCompressorBuilder}
     */
    public static Lz4FramedCompressorBuilder builder(OutputStream outputStream) {
        return new Lz4FramedCompressorBuilder(outputStream);
    }

    /**
     * Builder for creating a {@link FramedLZ4CompressorOutputStream}.
     *
     * @param <P> The type of the parent builder.
     */
    public static class Lz4FramedCompressorOutputStreamBuilder<P> {
        private final P parent;

        /** The output stream to write to. */
        protected final OutputStream outputStream;

        /**
         * Create a new {@link Lz4FramedCompressorOutputStreamBuilder} with the given parent and output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write to
         */
        public Lz4FramedCompressorOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Builds the {@link FramedLZ4CompressorOutputStream}.
         *
         * @return the {@link FramedLZ4CompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         */
        public FramedLZ4CompressorOutputStream build() throws IOException {
            return new FramedLZ4CompressorOutputStream(outputStream);
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

    /** Builder for creating a {@link Lz4FramedCompressor}. */
    public static class Lz4FramedCompressorBuilder
            extends CompressorBuilder<
                    FramedLZ4CompressorOutputStream, Lz4FramedCompressorBuilder, Lz4FramedCompressor> {

        private final Lz4FramedCompressorOutputStreamBuilder<Lz4FramedCompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link Lz4FramedCompressorBuilder} with the given path.
         *
         * @param path the path to write the compressor to
         * @throws IOException if an I/O error occurred
         */
        public Lz4FramedCompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link Lz4FramedCompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        public Lz4FramedCompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private Lz4FramedCompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new Lz4FramedCompressorOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Returns the output stream builder for this compressor.
         *
         * @return the {@link Lz4FramedCompressorOutputStreamBuilder}
         */
        public Lz4FramedCompressorOutputStreamBuilder<Lz4FramedCompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public Lz4FramedCompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public FramedLZ4CompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /** {@inheritDoc} */
        @Override
        public Lz4FramedCompressor build() throws IOException {
            return new Lz4FramedCompressor(this);
        }
    }
}
