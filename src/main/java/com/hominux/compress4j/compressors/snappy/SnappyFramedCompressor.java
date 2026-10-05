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
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream;

/**
 * Provides framed Snappy compression that writes to a {@link FramedSnappyCompressorOutputStream}.
 *
 * @since 3.2
 */
public class SnappyFramedCompressor extends CodecCompressor<FramedSnappyCompressorOutputStream> {

    /**
     * Constructor that takes a FramedSnappyCompressorOutputStream.
     *
     * @param compressorOutputStream the FramedSnappyCompressorOutputStream to write to.
     */
    public SnappyFramedCompressor(FramedSnappyCompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructor that takes a SnappyFramedCompressorBuilder.
     *
     * @param builder the SnappyFramedCompressorBuilder to build from.
     * @throws IOException if an I/O error occurred
     */
    public SnappyFramedCompressor(SnappyFramedCompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link SnappyFramedCompressorBuilder} that writes to the given path.
     *
     * @param path the path to write the compressor to
     * @return a new {@link SnappyFramedCompressorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static SnappyFramedCompressorBuilder builder(Path path) throws IOException {
        return new SnappyFramedCompressorBuilder(path);
    }

    /**
     * Creates a {@link SnappyFramedCompressorBuilder} that writes to the given stream.
     *
     * @param outputStream the output stream
     * @return a new {@link SnappyFramedCompressorBuilder}
     */
    public static SnappyFramedCompressorBuilder builder(OutputStream outputStream) {
        return new SnappyFramedCompressorBuilder(outputStream);
    }

    /**
     * Builder for creating a {@link FramedSnappyCompressorOutputStream}.
     *
     * @param <P> The type of the parent builder.
     */
    public static class SnappyFramedCompressorOutputStreamBuilder<P> {
        private final P parent;

        /** The output stream to write to. */
        protected final OutputStream outputStream;

        /**
         * Create a new {@link SnappyFramedCompressorOutputStreamBuilder} with the given parent and output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write to
         */
        public SnappyFramedCompressorOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Builds the {@link FramedSnappyCompressorOutputStream}.
         *
         * @return the {@link FramedSnappyCompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         */
        public FramedSnappyCompressorOutputStream build() throws IOException {
            return new FramedSnappyCompressorOutputStream(outputStream);
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

    /** Builder for creating a {@link SnappyFramedCompressor}. */
    public static class SnappyFramedCompressorBuilder
            extends CompressorBuilder<
                    FramedSnappyCompressorOutputStream, SnappyFramedCompressorBuilder, SnappyFramedCompressor> {

        private final SnappyFramedCompressorOutputStreamBuilder<SnappyFramedCompressorBuilder>
                compressorOutputStreamBuilder;

        /**
         * Create a new {@link SnappyFramedCompressorBuilder} with the given path.
         *
         * @param path the path to write the compressor to
         * @throws IOException if an I/O error occurred
         */
        public SnappyFramedCompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link SnappyFramedCompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        public SnappyFramedCompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private SnappyFramedCompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new SnappyFramedCompressorOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Returns the output stream builder for this compressor.
         *
         * @return the {@link SnappyFramedCompressorOutputStreamBuilder}
         */
        public SnappyFramedCompressorOutputStreamBuilder<SnappyFramedCompressorBuilder>
                compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public SnappyFramedCompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public FramedSnappyCompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /** {@inheritDoc} */
        @Override
        public SnappyFramedCompressor build() throws IOException {
            return new SnappyFramedCompressor(this);
        }
    }
}
